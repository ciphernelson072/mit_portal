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
import com.sis.repository.TeacherResultStatusNotificationEventRepository;
import com.sis.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import javax.persistence.EntityManager;
import java.util.Optional;
import java.util.List;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@DataJpaTest(properties = "sis.sample-data.enabled=false")
@Import(AcademicReportService.class)
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
    private TeacherResultStatusNotificationEventRepository statusNotificationEvents;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private AcademicReportService academicReportService;

    @MockBean
    private StudentService studentService;

    @MockBean
    private NotificationService notifications;

    @Test
    void approvalPersistsAndRemainsAvailableAfterReload() {
        Student student = studentRepository.saveAndFlush(
                new Student(new User("approval-student", "encoded", Role.STUDENT), "Approval Student", "Year 1"));
        userRepository.saveAndFlush(new User("registrar", "encoded", Role.ADMIN));
        userRepository.saveAndFlush(new User("registrar-2", "encoded", Role.ADMIN));
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

        var approvedReport = academicReportService.approveAcademicReport(student.getId(), "registrar");
        assertThat(approvedReport.getApproved()).isTrue();
        assertThat(approvedReport.getPublished()).isFalse();
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
        assertThatThrownBy(() -> academicReportService.publishAcademicReport(
                student.getId(), approvedReport.getResultsVersion(), "registrar"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("changed after approval");

        var correctedReport = academicReportService.approveAcademicReport(student.getId(), "registrar-2");
        academicReportService.publishAcademicReport(
                student.getId(), correctedReport.getResultsVersion(), "registrar-2");
        entityManager.flush();
        entityManager.clear();

        assertThat(academicReportService.isOfficiallyApproved(student.getId())).isTrue();
        assertThat(academicReportService.getApprovalStatus(student.getId()).getApprovedBy()).isEqualTo("registrar-2");
        assertThat(academicReportService.isOfficiallyPublished(student.getId())).isTrue();
        assertThat(academicReportService.getPublishedAcademicReport(student.getId()).getCgpa())
                .isEqualByComparingTo("5.00");
        assertThat(auditLogRepository.findTop100ByOrderByCreatedAtDesc()).extracting(AuditLog::getAction)
                .contains("ACADEMIC_RESULTS_APPROVED", "ACADEMIC_RESULTS_PUBLISHED");

        Grade changedAfterPublication = gradeRepository.findById(grade.getId())
                .orElseThrow(() -> new IllegalStateException("grade missing"));
        changedAfterPublication.setRemarks("corrected after publication");
        gradeRepository.saveAndFlush(changedAfterPublication);
        entityManager.flush();
        entityManager.clear();
        assertThat(academicReportService.isOfficiallyPublished(student.getId())).isFalse();
        assertThat(academicReportService.getStudentVisibleReport(student.getId()).getCgpa()).isNull();
    }

    @Test
    void publishedResultChangesAreReportedAndNotifiedOnlyOnce() {
        ResultStatusFixture fixture = createResultStatusFixture("publication", GradeStatus.APPROVED);
        List<String> courseCodes = List.of(fixture.course().getCourseCode());

        assertThat(academicReportService.getTeacherPublicationStatus(
                fixture.student().getId(), fixture.student().getFullName(), courseCodes, fixture.teacherUserId())
                .getStatus()).isEqualTo("PENDING");
        var approvedReport = academicReportService.approveAcademicReport(
                fixture.student().getId(), fixture.adminUsername());
        assertThat(academicReportService.getTeacherPublicationStatus(
                fixture.student().getId(), fixture.student().getFullName(), courseCodes, fixture.teacherUserId())
                .getStatus()).isEqualTo("AWAITING_PUBLICATION");
        academicReportService.publishAcademicReport(
                fixture.student().getId(), approvedReport.getResultsVersion(), fixture.adminUsername());
        assertThat(academicReportService.getTeacherPublicationStatus(
                fixture.student().getId(), fixture.student().getFullName(), courseCodes, fixture.teacherUserId())
                .getStatus()).isEqualTo("PUBLISHED");

        entityManager.flush();
        entityManager.clear();
        Grade changedGrade = gradeRepository.findById(fixture.grade().getId()).orElseThrow();
        changedGrade.setRemarks("corrected after publication");
        gradeRepository.saveAndFlush(changedGrade);

        assertThat(academicReportService.getTeacherPublicationStatus(
                fixture.student().getId(), fixture.student().getFullName(), courseCodes, fixture.teacherUserId())
                .getStatus()).isEqualTo("INVALIDATED");
        assertThat(academicReportService.getTeacherPublicationStatus(
                fixture.student().getId(), fixture.student().getFullName(), courseCodes, fixture.teacherUserId())
                .getStatus()).isEqualTo("INVALIDATED");

        assertThat(statusNotificationEvents.count()).isEqualTo(1);
        verify(notifications, times(1)).create(eq(fixture.teacherUserId()), eq("Student results published"),
                anyString(), eq(NotificationCategory.GRADE));
        verify(notifications, times(1)).create(eq(fixture.teacherUserId()), eq("Published results invalidated"),
                anyString(), eq(NotificationCategory.GRADE));
    }

    @Test
    void blockedResultsNotifyTheAssignedTeacherOnlyOnce() {
        ResultStatusFixture fixture = createResultStatusFixture("blocked", GradeStatus.REJECTED);
        List<String> courseCodes = List.of(fixture.course().getCourseCode());

        assertThat(academicReportService.getTeacherPublicationStatus(
                fixture.student().getId(), fixture.student().getFullName(), courseCodes, fixture.teacherUserId())
                .getStatus()).isEqualTo("BLOCKED");
        assertThat(academicReportService.getTeacherPublicationStatus(
                fixture.student().getId(), fixture.student().getFullName(), courseCodes, fixture.teacherUserId())
                .getStatus()).isEqualTo("BLOCKED");

        assertThat(statusNotificationEvents.count()).isEqualTo(1);
        verify(notifications, times(1)).create(eq(fixture.teacherUserId()), eq("Student results blocked"),
                anyString(), eq(NotificationCategory.GRADE));
    }

    private ResultStatusFixture createResultStatusFixture(String suffix, GradeStatus status) {
        Student student = studentRepository.saveAndFlush(new Student(
                new User("status-student-" + suffix, "encoded", Role.STUDENT), "Status Student", "Year 1"));
        Teacher teacher = teacherRepository.saveAndFlush(new Teacher(
                new User("status-teacher-" + suffix, "encoded", Role.TEACHER), "Status Teacher"));
        String adminUsername = "status-registrar-" + suffix;
        userRepository.saveAndFlush(new User(adminUsername, "encoded", Role.ADMIN));
        CourseCategory category = courseCategories.saveAndFlush(
                new CourseCategory("STATUS", "Status Category"));
        Course course = new Course();
        course.setName("Status Workflow");
        course.setCourseCode("STATUS101");
        course.setCategory(category);
        course.setTeacher(teacher);
        course.setCredits(3);
        course.setSemester("Semester 1");
        course = courseRepository.saveAndFlush(course);

        Grade grade = new Grade(student, course, "A", "initial result");
        grade.setGpaScore(new BigDecimal("5.00"));
        grade.setStatus(status);
        if (status == GradeStatus.APPROVED) {
            grade.setApprovedBy("registrar");
            grade.setApprovedAt(LocalDateTime.now());
        }
        grade = gradeRepository.saveAndFlush(grade);
        given(studentService.getStudentById(student.getId())).willReturn(Optional.of(student));
        return new ResultStatusFixture(student, course, grade, teacher.getUser().getId(), adminUsername);
    }

    private record ResultStatusFixture(
            Student student, Course course, Grade grade, Long teacherUserId, String adminUsername) {
    }
}
