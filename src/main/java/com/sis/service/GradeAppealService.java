package com.sis.service;

import com.sis.dto.GradeAppealDto;
import com.sis.entity.AuditLog;
import com.sis.entity.Grade;
import com.sis.entity.GradeAppeal;
import com.sis.entity.GradeAppealStatus;
import com.sis.entity.GradeStatus;
import com.sis.entity.NotificationCategory;
import com.sis.entity.Role;
import com.sis.entity.User;
import com.sis.exception.ResourceNotFoundException;
import com.sis.repository.AuditLogRepository;
import com.sis.repository.GradeAppealRepository;
import com.sis.repository.GradeRepository;
import com.sis.repository.TeacherRepository;
import com.sis.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

@Service
@Transactional
public class GradeAppealService {

    private static final List<GradeAppealStatus> ACTIVE_STATUSES = List.of(
            GradeAppealStatus.OPEN,
            GradeAppealStatus.TEACHER_REVIEW,
            GradeAppealStatus.READY_FOR_DECISION);

    private final GradeAppealRepository appeals;
    private final GradeRepository grades;
    private final TeacherRepository teachers;
    private final UserRepository users;
    private final AuditLogRepository auditLogs;
    private final AcademicReportService reports;
    private final NotificationService notifications;

    public GradeAppealService(GradeAppealRepository appeals, GradeRepository grades,
                              TeacherRepository teachers, UserRepository users,
                              AuditLogRepository auditLogs, AcademicReportService reports,
                              NotificationService notifications) {
        this.appeals = appeals;
        this.grades = grades;
        this.teachers = teachers;
        this.users = users;
        this.auditLogs = auditLogs;
        this.reports = reports;
        this.notifications = notifications;
    }

    public GradeAppealDto submit(Long gradeId, String reason, String studentUsername) {
        Grade grade = grades.findByIdForUpdate(gradeId)
                .orElseThrow(() -> new ResourceNotFoundException("Grade not found."));
        if (grade.getStudent() == null || grade.getStudent().getUser() == null
                || !studentUsername.equals(grade.getStudent().getUser().getUsername())) {
            throw new ResourceNotFoundException("Grade not found.");
        }
        if (grade.getStatus() != GradeStatus.APPROVED || !reports.isOfficiallyPublished(grade.getStudent().getId())) {
            throw new IllegalStateException("Only a grade in currently published official results can be appealed.");
        }
        if (appeals.existsByGradeIdAndStatusIn(gradeId, ACTIVE_STATUSES)) {
            throw new IllegalStateException("An open appeal already exists for this grade.");
        }
        String normalizedReason = reason == null ? "" : reason.trim();
        if (normalizedReason.isEmpty()) {
            throw new IllegalArgumentException("An appeal reason is required.");
        }

        GradeAppeal saved = appeals.saveAndFlush(new GradeAppeal(grade, normalizedReason, LocalDateTime.now()));
        audit(studentUsername, "GRADE_APPEAL_SUBMITTED", "Appeal #" + saved.getId()
                + " submitted for grade #" + gradeId + ".");
        notifyAdministrators("Student grade appeal submitted",
                "Grade appeal #" + saved.getId() + " requires review.");
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<GradeAppealDto> listForStudent(String username) {
        return appeals.findByStudentUsername(username).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<GradeAppealDto> listAll() {
        return appeals.findAllWithGradeStudentAndCourse().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<GradeAppealDto> listForTeacherCourses(Collection<Long> courseIds) {
        if (courseIds == null || courseIds.isEmpty()) {
            return List.of();
        }
        return appeals.findByCourseIdsAndStatus(courseIds, GradeAppealStatus.TEACHER_REVIEW)
                .stream().map(this::toDto).toList();
    }

    public GradeAppealDto requestTeacherConsultation(Long appealId, String actor) {
        GradeAppeal appeal = findForUpdate(appealId);
        requireStatus(appeal, GradeAppealStatus.OPEN);
        var teacher = appeal.getGrade().getCourse().getTeacher();
        if (teacher == null || teacher.getUser() == null || teacher.getUser().getId() == null) {
            throw new IllegalStateException("The assigned teacher account is unavailable for consultation.");
        }
        appeal.requestTeacherConsultation(actor, LocalDateTime.now());
        audit(actor, "GRADE_APPEAL_TEACHER_CONSULTATION_REQUESTED",
                "Teacher consultation requested for appeal #" + appealId + ".");
        notifications.create(teacher.getUser().getId(), "Student grade appeal requires consultation",
                "Please review and respond to appeal #" + appealId + " for "
                        + appeal.getGrade().getCourse().getCourseCode() + ".",
                NotificationCategory.GRADE);
        return toDto(appeal);
    }

    public GradeAppealDto respondAsTeacher(Long appealId, String response, String teacherUsername) {
        GradeAppeal appeal = findForUpdate(appealId);
        requireStatus(appeal, GradeAppealStatus.TEACHER_REVIEW);
        var teacher = appeal.getGrade().getCourse().getTeacher();
        if (teacher == null || teacher.getUser() == null
                || !teacherUsername.equals(teacher.getUser().getUsername())) {
            throw new ResourceNotFoundException("Appeal not found.");
        }
        String normalizedResponse = response == null ? "" : response.trim();
        if (normalizedResponse.isEmpty()) {
            throw new IllegalArgumentException("A teacher response is required when submitting a response.");
        }
        appeal.recordTeacherResponse(normalizedResponse, teacherUsername, LocalDateTime.now());
        audit(teacherUsername, "GRADE_APPEAL_TEACHER_RESPONSE",
                "Teacher response recorded for appeal #" + appealId + ".");
        notifyAdministrators("Grade appeal ready for decision",
                "The teacher responded to appeal #" + appealId + ". Record the decision in the portal.");
        return toDto(appeal);
    }

    public GradeAppealDto confirmTeacherConsultation(Long appealId, String actor) {
        GradeAppeal appeal = findForUpdate(appealId);
        requireStatus(appeal, GradeAppealStatus.TEACHER_REVIEW);
        appeal.confirmTeacherConsultation(actor, LocalDateTime.now());
        audit(actor, "GRADE_APPEAL_TEACHER_CONSULTATION_CONFIRMED",
                "Teacher consultation confirmed without a written response for appeal #" + appealId + ".");
        notifyAdministrators("Grade appeal ready for decision",
                "Teacher consultation was confirmed for appeal #" + appealId + ". Record the decision in the portal.");
        return toDto(appeal);
    }

    public GradeAppealDto decide(Long appealId, String decision, String note, String actor) {
        GradeAppeal appeal = findForUpdate(appealId);
        requireStatus(appeal, GradeAppealStatus.READY_FOR_DECISION);
        GradeAppealStatus nextStatus = switch (decision == null ? "" : decision.trim().toUpperCase(Locale.ROOT)) {
            case "APPROVED" -> GradeAppealStatus.APPROVED;
            case "REJECTED" -> GradeAppealStatus.REJECTED;
            default -> throw new IllegalArgumentException("Appeal decision must be APPROVED or REJECTED.");
        };
        String normalizedNote = note == null ? "" : note.trim();
        if (normalizedNote.isEmpty()) {
            throw new IllegalArgumentException("A decision note is required.");
        }
        appeal.decide(nextStatus, actor, normalizedNote, LocalDateTime.now());
        audit(actor, "GRADE_APPEAL_" + nextStatus.name(),
                "Appeal #" + appealId + " was " + nextStatus.name().toLowerCase() + ".");
        Long studentUserId = appeal.getGrade().getStudent().getUser().getId();
        if (studentUserId == null) {
            throw new IllegalStateException("The student account is unavailable for appeal notification.");
        }
        String message = nextStatus == GradeAppealStatus.APPROVED
                ? "Your grade appeal #" + appealId + " was approved for review. This does not change the grade; any correction follows the separate academic correction process."
                : "Your grade appeal #" + appealId + " was not approved. Review the decision note in your portal.";
        notifications.create(studentUserId, "Grade appeal decision", message, NotificationCategory.GRADE);
        return toDto(appeal);
    }

    private GradeAppeal findForUpdate(Long appealId) {
        return appeals.findByIdForUpdate(appealId)
                .orElseThrow(() -> new ResourceNotFoundException("Grade appeal not found."));
    }

    private void requireStatus(GradeAppeal appeal, GradeAppealStatus required) {
        if (appeal.getStatus() != required) {
            throw new IllegalStateException("Appeal must be " + required.name() + " before this action.");
        }
    }

    private void notifyAdministrators(String title, String message) {
        users.findByRoleAndIsActiveTrue(Role.ADMIN).forEach(admin ->
                notifications.create(admin.getId(), title, message, NotificationCategory.GRADE));
    }

    private void audit(String username, String action, String details) {
        User actor = users.findByUsernameNormalized(username)
                .orElseThrow(() -> new IllegalStateException("The authenticated appeal workflow actor was not found."));
        auditLogs.save(new AuditLog(actor.getId(), action, details));
    }

    private GradeAppealDto toDto(GradeAppeal appeal) {
        Grade grade = appeal.getGrade();
        var student = grade.getStudent();
        var course = grade.getCourse();
        return new GradeAppealDto(appeal.getId(), grade.getId(),
                student == null ? null : student.getFullName(),
                student == null ? null : student.getRegistrationNumber(),
                course == null ? null : course.getCourseCode(),
                course == null ? null : course.getName(),
                appeal.getReason(), appeal.getStatus().name(), appeal.getSubmittedAt(),
                appeal.getConsultationRequestedAt(), appeal.getTeacherResponse(),
                appeal.getTeacherRespondedAt(), appeal.getTeacherConsultedAt(),
                appeal.getDecisionBy(), appeal.getDecidedAt(), appeal.getDecisionNote());
    }
}
