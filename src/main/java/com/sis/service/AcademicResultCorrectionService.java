package com.sis.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sis.dto.GradeCorrectionDto;
import com.sis.entity.*;
import com.sis.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.*;

@Service
@Transactional
public class AcademicResultCorrectionService {

    private static final List<GradeCorrectionStatus> ACTIVE_STATUSES = List.of(
            GradeCorrectionStatus.AUTHORIZED,
            GradeCorrectionStatus.IN_PROGRESS,
            GradeCorrectionStatus.SUBMITTED);

    private final GradeRepository grades;
    private final GradeCorrectionRequestRepository corrections;
    private final GradeAssessmentMarkRepository marks;
    private final UserRepository users;
    private final AuditLogRepository auditLogs;
    private final ObjectMapper objectMapper;
    @Autowired(required = false)
    private NotificationService notifications;

    public AcademicResultCorrectionService(GradeRepository grades,
                                           GradeCorrectionRequestRepository corrections,
                                           GradeAssessmentMarkRepository marks,
                                           UserRepository users,
                                           AuditLogRepository auditLogs,
                                           ObjectMapper objectMapper) {
        this.grades = grades;
        this.corrections = corrections;
        this.marks = marks;
        this.users = users;
        this.auditLogs = auditLogs;
        this.objectMapper = objectMapper;
    }

    public GradeCorrectionDto authorize(Long gradeId, String reason, String authorizedBy) {
        Grade grade = grades.findByIdForUpdate(gradeId)
                .orElseThrow(() -> new IllegalArgumentException("Grade not found: " + gradeId));
        if (grade.getStatus() != GradeStatus.APPROVED) {
            throw new IllegalStateException("Only approved grades can enter the correction workflow.");
        }
        if (corrections.existsByGradeIdAndStatusIn(gradeId, ACTIVE_STATUSES)) {
            throw new IllegalStateException("An active correction already exists for this grade.");
        }
        String normalizedReason = reason == null ? "" : reason.trim();
        if (normalizedReason.isEmpty()) {
            throw new IllegalArgumentException("A correction reason is required.");
        }

        GradeCorrectionRequest correction = new GradeCorrectionRequest(
                grade, normalizedReason, snapshot(grade), authorizedBy, LocalDateTime.now());
        GradeCorrectionRequest saved = corrections.save(correction);
        audit(authorizedBy, "GRADE_CORRECTION_AUTHORIZED",
                "Correction #" + saved.getId() + " authorized for grade #" + gradeId);
        notifySubmittingTeacher(saved);
        return toDto(saved);
    }

    public List<GradeCorrectionDto> listForCourses(List<Long> courseIds, Collection<GradeCorrectionStatus> statuses) {
        if (courseIds == null || courseIds.isEmpty()) {
            return List.of();
        }
        return corrections.findByGradeCourseIdInAndStatusIn(courseIds, statuses).stream()
                .sorted(Comparator.comparing(GradeCorrectionRequest::getId).reversed())
                .map(this::toDto)
                .toList();
    }

    public List<GradeCorrectionDto> listForStudent(String username, Collection<GradeCorrectionStatus> statuses) {
        return corrections.findByGradeStudentUserUsernameAndStatusIn(username, statuses).stream()
                .sorted(Comparator.comparing(GradeCorrectionRequest::getId).reversed())
                .map(this::toDto)
                .toList();
    }

    private void notifySubmittingTeacher(GradeCorrectionRequest correction) {
        if (notifications == null || correction.getGrade().getSubmittedBy() == null) {
            return;
        }
        users.findByUsernameNormalized(correction.getGrade().getSubmittedBy()).ifPresent(user -> {
            if (user.getId() != null) {
                notifications.create(user.getId(), "Grade correction authorized",
                        "A correction has been authorized for grade #" + correction.getGrade().getId()
                                + ". Reason: " + correction.getReason(),
                        NotificationCategory.GRADE);
            }
        });
    }

    public Grade beginAuthorizedCorrection(Long gradeId, String actor) {
        Grade grade = grades.findByIdForUpdate(gradeId)
                .orElseThrow(() -> new IllegalArgumentException("Grade not found: " + gradeId));
        if (grade.getStatus() == GradeStatus.APPROVED) {
            if (actor == null || actor.isBlank()) {
                throw new IllegalArgumentException("The correction actor is required.");
            }
            GradeCorrectionRequest correction = corrections
                    .findTopByGradeIdAndStatusInOrderByIdDesc(gradeId,
                            List.of(GradeCorrectionStatus.AUTHORIZED))
                    .orElseThrow(() -> new IllegalStateException(
                            "Approved grades cannot be changed without administrator authorization."));
            correction.setStatus(GradeCorrectionStatus.IN_PROGRESS);
            grade.setStatus(GradeStatus.PENDING);
            grade.setApprovedBy(null);
            grade.setApprovedAt(null);
            grade.setAssessmentReviewFingerprint(null);
            grade.setAssessmentReviewedBy(null);
            grade.setAssessmentReviewedAt(null);
            grades.save(grade);
            audit(actor, "GRADE_CORRECTION_STARTED",
                    "Correction #" + correction.getId() + " started for grade #" + gradeId);
        } else if (corrections.existsByGradeIdAndStatusIn(gradeId,
                List.of(GradeCorrectionStatus.AUTHORIZED))) {
            throw new IllegalStateException("The authorized correction is inconsistent with the current grade status.");
        }
        return grade;
    }

    public void markSubmitted(Long gradeId, String actor) {
        corrections.findTopByGradeIdAndStatusInOrderByIdDesc(
                        gradeId, List.of(GradeCorrectionStatus.IN_PROGRESS))
                .ifPresent(correction -> {
                    correction.setStatus(GradeCorrectionStatus.SUBMITTED);
                    correction.setSubmittedAt(LocalDateTime.now());
                    audit(actor, "GRADE_CORRECTION_SUBMITTED",
                            "Correction #" + correction.getId() + " resubmitted for grade #" + gradeId);
                });
    }

    public void markApproved(Long gradeId, String actor) {
        corrections.findTopByGradeIdAndStatusInOrderByIdDesc(
                        gradeId, List.of(GradeCorrectionStatus.SUBMITTED))
                .ifPresent(correction -> {
                    correction.setStatus(GradeCorrectionStatus.COMPLETED);
                    correction.setCompletedAt(LocalDateTime.now());
                    audit(actor, "GRADE_CORRECTION_COMPLETED",
                            "Correction #" + correction.getId() + " completed after grade approval.");
                });
    }

    public void markRejected(Long gradeId, String actor) {
        corrections.findTopByGradeIdAndStatusInOrderByIdDesc(
                        gradeId, List.of(GradeCorrectionStatus.SUBMITTED))
                .ifPresent(correction -> {
                    correction.setStatus(GradeCorrectionStatus.IN_PROGRESS);
                    audit(actor, "GRADE_CORRECTION_CHANGES_REQUIRED",
                            "Correction #" + correction.getId() + " requires changes before resubmission.");
                });
    }

    public boolean hasHistory(Long gradeId) {
        return corrections.existsByGradeId(gradeId);
    }

    public boolean hasActiveCorrectionsForCourse(Long courseId) {
        return corrections.existsByGradeCourseIdAndStatusIn(courseId, ACTIVE_STATUSES);
    }

    private String snapshot(Grade grade) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("gradeValue", grade.getGradeValue());
        result.put("gpaScore", grade.getGpaScore());
        result.put("totalMark", grade.getTotalMark());
        result.put("remarks", grade.getRemarks());
        List<Map<String, Object>> assessmentMarks = new ArrayList<>();
        marks.findByGradeId(grade.getId()).stream()
                .sorted(Comparator.comparing(GradeAssessmentMark::getId))
                .forEach(mark -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("componentId", mark.getComponent().getId());
                    item.put("componentCode", mark.getComponent().getComponentCode());
                    item.put("score", mark.getScore());
                    item.put("remarks", mark.getRemarks());
                    assessmentMarks.add(item);
                });
        result.put("assessmentMarks", assessmentMarks);
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("The approved result snapshot could not be recorded.", exception);
        }
    }

    private GradeCorrectionDto toDto(GradeCorrectionRequest correction) {
        return new GradeCorrectionDto(correction.getId(), correction.getGrade().getId(),
                correction.getStatus().name(), correction.getReason(), correction.getPreviousSnapshot(),
                correction.getAuthorizedBy(), correction.getAuthorizedAt(), correction.getSubmittedAt(),
                correction.getCompletedAt());
    }

    private void audit(String username, String action, String details) {
        Long userId = users.findByUsernameNormalized(username)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + username))
                .getId();
        auditLogs.save(new AuditLog(userId, action, details));
    }
}
