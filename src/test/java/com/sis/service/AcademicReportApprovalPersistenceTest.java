package com.sis.service;

import com.sis.entity.Role;
import com.sis.entity.Course;
import com.sis.entity.CourseCategory;
import com.sis.entity.AuditLog;
import com.sis.entity.Grade;
import com.sis.entity.GradeStatus;
import com.sis.entity.NotificationCategory;
import com.sis.entity.Student;
import com.sis.entity.Teacher;
import com.sis.entity.User;
import com.sis.repository.AcademicReportApprovalRepository;
import com.sis.repository.AuditLogRepository;
import com.sis.repository.CourseCategoryRepository;
import com.sis.repository.CourseRepository;
import com.sis.repository.GradeRepository;
import com.sis.repository.StudentRepository;
import com.sis.repository.TeacherRepository;
import com.sis.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;

import jakarta.persistence.EntityManager;
import java.util.Optional;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@DataJpaTest(properties = "sis.sample-data.enabled=false")
@Import({AcademicReportService.class, GradeAppealService.class})
class AcademicReportApprovalPersistenceTest {

    @Autowired
    private AcademicReportApprovalRepository approvalRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private CourseCategoryRepository courseCategories;

    @Autowired
    private TeacherRepository teacherRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private GradeRepository gradeRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private AcademicReportService academicReportService;

    @Autowired
    private GradeAppealService gradeAppealService;

    @MockitoBean
    private StudentService studentService;

    @MockitoBean
    private NotificationService notifications;

    @Test
    void approvalPersistsAndRemainsAvailableAfterReload() {
        Student student = studentRepository.saveAndFlush(
                new Student(new User("approval-student", "encoded", Role.STUDENT), "Approval Student", "Year 1"));
        userRepository.saveAndFlush(new User("different-student", "encoded", Role.STUDENT));
        User registrar = userRepository.saveAndFlush(new User("registrar", "encoded", Role.ADMIN));
        User secondRegistrar = userRepository.saveAndFlush(new User("registrar-2", "encoded", Role.ADMIN));
        given(studentService.getStudentById(student.getId())).willReturn(Optional.of(student));

        CourseCategory category = courseCategories.saveAndFlush(
                new CourseCategory("TEST", "Test Category"));
        Teacher teacher = teacherRepository.saveAndFlush(
                new Teacher(new User("approval-teacher", "encoded", Role.TEACHER), "Approval Teacher"));
        Course course = new Course();
        course.setName("Academic Policy");
        course.setCourseCode("TEST101");
        course.setCategory(category);
        course.setTeacher(teacher);
        course.setCredits(3);
        course.setSemester("Semester 1");
        course = courseRepository.saveAndFlush(course);

        Grade grade = new Grade(student, course, "A", "approved");
        grade.setGpaScore(new BigDecimal("5.00"));
        grade.setStatus(GradeStatus.APPROVED);
        grade.setApprovedBy("registrar");
        grade.setApprovedAt(LocalDateTime.now());
        gradeRepository.saveAndFlush(grade);

        assertThatThrownBy(() -> gradeAppealService.submit(grade.getId(),
                "Unpublished result.", "approval-student"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("currently published official results");
        assertThat(academicReportService.getTeacherPublicationStatus(
                student.getId(), student.getFullName(), java.util.List.of("TEST101"),
                teacher.getUser().getId()).getStatus())
                .isEqualTo("PENDING");
        var approvedReport = academicReportService.approveAcademicReport(student.getId(), "registrar");
        assertThat(approvedReport.getApproved()).isTrue();
        assertThat(approvedReport.getPublished()).isFalse();
        assertThat(academicReportService.getTeacherPublicationStatus(
                student.getId(), student.getFullName(), java.util.List.of("TEST101"),
                teacher.getUser().getId()).getStatus())
                .isEqualTo("AWAITING_PUBLICATION");
        assertThat(academicReportService.getStudentVisibleReport(student.getId()).getCgpa()).isNull();
        assertThat(academicReportService.getStudentVisibleReport(student.getId()).getCourseGrades()).isEmpty();
        entityManager.flush();
        entityManager.clear();

        Grade versionBeforePublication = gradeRepository.findById(grade.getId())
                .orElseThrow(() -> new IllegalStateException("grade missing"));
        versionBeforePublication.setRemarks("corrected before publication");
        gradeRepository.saveAndFlush(versionBeforePublication);
        entityManager.flush();
        entityManager.clear();
        var staleUnpublishedStatus = academicReportService.getTeacherPublicationStatus(
                student.getId(), student.getFullName(), java.util.List.of("TEST101"),
                teacher.getUser().getId());
        assertThat(staleUnpublishedStatus.getStatus()).isEqualTo("PENDING");
        assertThat(staleUnpublishedStatus.getMessage()).contains("re-approval");
        assertThatThrownBy(() -> academicReportService.publishAcademicReport(
                student.getId(), approvedReport.getResultsVersion(), "registrar"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("changed after approval");

        var correctedReport = academicReportService.approveAcademicReport(student.getId(), "registrar-2");
        academicReportService.publishAcademicReport(
                student.getId(), correctedReport.getResultsVersion(), "registrar-2");
        assertThat(academicReportService.getTeacherPublicationStatus(
                student.getId(), student.getFullName(), java.util.List.of("TEST101"),
                teacher.getUser().getId()).getStatus())
                .isEqualTo("PUBLISHED");
        academicReportService.publishAcademicReport(
                student.getId(), correctedReport.getResultsVersion(), "registrar-2");
        verify(notifications, times(1)).create(
                student.getUser().getId(),
                "Academic results published",
                "Your approved academic results are now available in the student portal.",
                NotificationCategory.GRADE);
        verify(notifications, times(1)).create(
                teacher.getUser().getId(),
                "Student results published",
                "Approved results for Approval Student including TEST101 are now available in the student portal.",
                NotificationCategory.GRADE);
        entityManager.flush();
        entityManager.clear();

        assertThat(academicReportService.isOfficiallyApproved(student.getId())).isTrue();
        assertThat(academicReportService.getApprovalStatus(student.getId()).getApprovedBy()).isEqualTo("registrar-2");
        assertThat(academicReportService.isOfficiallyPublished(student.getId())).isTrue();
        assertThat(academicReportService.getPublishedAcademicReport(student.getId()).getCgpa())
                .isEqualByComparingTo("5.00");
        assertThatThrownBy(() -> gradeAppealService.submit(grade.getId(),
                "Another student's appeal.", "different-student"))
                .isInstanceOf(com.sis.exception.ResourceNotFoundException.class)
                .hasMessage("Grade not found.");
        var appeal = gradeAppealService.submit(grade.getId(),
                "Please review the recorded assessment result.", "approval-student");
        assertThat(appeal.getStatus()).isEqualTo("OPEN");
        assertThat(appeal.getTeacherResponse()).isNull();
        verify(notifications).create(
                registrar.getId(),
                "Student grade appeal submitted",
                "Grade appeal #" + appeal.getId() + " requires review.",
                NotificationCategory.GRADE);
        verify(notifications).create(
                secondRegistrar.getId(),
                "Student grade appeal submitted",
                "Grade appeal #" + appeal.getId() + " requires review.",
                NotificationCategory.GRADE);
        assertThatThrownBy(() -> gradeAppealService.submit(grade.getId(),
                "Duplicate appeal.", "approval-student"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already exists");
        assertThatThrownBy(() -> gradeAppealService.decide(
                appeal.getId(), "APPROVED", "Premature decision.", "registrar"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("READY_FOR_DECISION");

        assertThat(gradeAppealService.requestTeacherConsultation(
                appeal.getId(), "registrar").getStatus()).isEqualTo("TEACHER_REVIEW");
        assertThat(gradeAppealService.respondAsTeacher(
                appeal.getId(), "I reviewed the assessment record.", "approval-teacher").getStatus())
                .isEqualTo("READY_FOR_DECISION");
        verify(notifications).create(
                registrar.getId(),
                "Grade appeal ready for decision",
                "The teacher responded to appeal #" + appeal.getId()
                        + ". Record the decision in the portal.",
                NotificationCategory.GRADE);
        verify(notifications).create(
                secondRegistrar.getId(),
                "Grade appeal ready for decision",
                "The teacher responded to appeal #" + appeal.getId()
                        + ". Record the decision in the portal.",
                NotificationCategory.GRADE);
        var appealDecision = gradeAppealService.decide(
                appeal.getId(), "APPROVED", "Proceed to the separate correction review.", "registrar");
        assertThat(appealDecision.getStatus()).isEqualTo("APPROVED");
        assertThat(gradeRepository.findById(grade.getId()).orElseThrow().getStatus())
                .isEqualTo(GradeStatus.APPROVED);
        assertThat(gradeRepository.findById(grade.getId()).orElseThrow().getGradeValue()).isEqualTo("A");
        verify(notifications).create(
                student.getUser().getId(),
                "Grade appeal decision",
                "Your grade appeal #" + appeal.getId()
                        + " was approved for review. This does not change the grade; any correction follows the separate academic correction process.",
                NotificationCategory.GRADE);
        verify(notifications).create(
                teacher.getUser().getId(),
                "Student grade appeal requires consultation",
                "Please review and respond to appeal #" + appeal.getId() + " for TEST101.",
                NotificationCategory.GRADE);
        var followupAppeal = gradeAppealService.submit(grade.getId(),
                "Please also verify the published assessment breakdown.", "approval-student");
        assertThat(gradeAppealService.requestTeacherConsultation(
                followupAppeal.getId(), "registrar").getStatus()).isEqualTo("TEACHER_REVIEW");
        var confirmedConsultation = gradeAppealService.confirmTeacherConsultation(
                followupAppeal.getId(), "registrar");
        assertThat(confirmedConsultation.getStatus()).isEqualTo("READY_FOR_DECISION");
        assertThat(confirmedConsultation.getTeacherResponse()).isNull();
        verify(notifications).create(
                registrar.getId(),
                "Grade appeal ready for decision",
                "Teacher consultation was confirmed for appeal #" + followupAppeal.getId()
                        + ". Record the decision in the portal.",
                NotificationCategory.GRADE);
        verify(notifications).create(
                secondRegistrar.getId(),
                "Grade appeal ready for decision",
                "Teacher consultation was confirmed for appeal #" + followupAppeal.getId()
                        + ". Record the decision in the portal.",
                NotificationCategory.GRADE);
        assertThat(gradeAppealService.decide(
                followupAppeal.getId(), "REJECTED", "The published assessment was verified.", "registrar")
                .getStatus()).isEqualTo("REJECTED");
        verify(notifications).create(
                teacher.getUser().getId(),
                "Student grade appeal requires consultation",
                "Please review and respond to appeal #" + followupAppeal.getId() + " for TEST101.",
                NotificationCategory.GRADE);
        assertThat(auditLogRepository.findTop100ByOrderByCreatedAtDesc()).extracting(AuditLog::getAction)
                .contains("ACADEMIC_RESULTS_APPROVED", "ACADEMIC_RESULTS_PUBLISHED",
                        "GRADE_APPEAL_SUBMITTED", "GRADE_APPEAL_TEACHER_RESPONSE",
                        "GRADE_APPEAL_TEACHER_CONSULTATION_CONFIRMED",
                        "GRADE_APPEAL_APPROVED", "GRADE_APPEAL_REJECTED");

        Grade changedAfterPublication = gradeRepository.findById(grade.getId())
                .orElseThrow(() -> new IllegalStateException("grade missing"));
        changedAfterPublication.setRemarks("corrected after publication");
        gradeRepository.saveAndFlush(changedAfterPublication);
        entityManager.flush();
        entityManager.clear();
        assertThat(academicReportService.isOfficiallyPublished(student.getId())).isFalse();
        assertThat(academicReportService.getStudentVisibleReport(student.getId()).getCgpa()).isNull();
        var invalidatedStatus = academicReportService.getTeacherPublicationStatus(
                student.getId(), student.getFullName(), java.util.List.of("TEST101"),
                teacher.getUser().getId());
        assertThat(invalidatedStatus.getStatus()).isEqualTo("INVALIDATED");
        assertThat(invalidatedStatus.getMessage()).doesNotContain("corrected after publication");
        assertThat(academicReportService.getTeacherPublicationStatus(
                student.getId(), student.getFullName(), java.util.List.of("TEST101"),
                teacher.getUser().getId()).getStatus()).isEqualTo("INVALIDATED");
        verify(notifications, times(1)).create(
                teacher.getUser().getId(),
                "Published results invalidated",
                "Published results for Approval Student in TEST101 changed and require administrative review.",
                NotificationCategory.GRADE);
    }

    @Test
    void rejectedGradeAppearsAsBlockedInTeacherPublicationStatus() {
        Student student = studentRepository.saveAndFlush(
                new Student(new User("blocked-student", "encoded", Role.STUDENT), "Blocked Student", "Year 1"));
        CourseCategory category = courseCategories.saveAndFlush(
                new CourseCategory("BLOCK", "Blocked Category"));
        Teacher teacher = teacherRepository.saveAndFlush(
                new Teacher(new User("blocked-teacher", "encoded", Role.TEACHER), "Blocked Teacher"));
        Course course = new Course();
        course.setName("Blocked Course");
        course.setCourseCode("BLOCK101");
        course.setCategory(category);
        course.setTeacher(teacher);
        course = courseRepository.saveAndFlush(course);
        Grade grade = new Grade(student, course, "C", "returned grade");
        grade.setStatus(GradeStatus.REJECTED);
        gradeRepository.saveAndFlush(grade);

        var status = academicReportService.getTeacherPublicationStatus(
                student.getId(), student.getFullName(), java.util.List.of("BLOCK101"),
                teacher.getUser().getId());

        assertThat(status.getStatus()).isEqualTo("BLOCKED");
        assertThat(status.getMessage()).contains("returned");
        academicReportService.getTeacherPublicationStatus(
                student.getId(), student.getFullName(), java.util.List.of("BLOCK101"),
                teacher.getUser().getId());
        verify(notifications, times(1)).create(
                teacher.getUser().getId(),
                "Student results blocked",
                "Results for Blocked Student in BLOCK101 are blocked and require resolution in the portal.",
                NotificationCategory.GRADE);
    }
}
