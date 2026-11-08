package com.sis;

import com.sis.entity.AssessmentComponentType;
import com.sis.entity.Course;
import com.sis.entity.CourseAssessmentComponent;
import com.sis.entity.Grade;
import com.sis.entity.GradeAssessmentMark;
import com.sis.entity.GradeStatus;
import com.sis.entity.Student;
import com.sis.entity.User;
import com.sis.repository.AuditLogRepository;
import com.sis.repository.CourseAssessmentComponentRepository;
import com.sis.repository.GradeAssessmentMarkRepository;
import com.sis.repository.GradeRepository;
import com.sis.repository.StudentRepository;
import com.sis.repository.UserRepository;
import com.sis.service.AssessmentCalculationService;
import com.sis.service.EmailService;
import com.sis.service.GradeApprovalService;
import com.sis.service.NotificationService;
import com.sis.service.StudentService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GradeApprovalEmailPreferenceTest {

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void gradeAndCgpaEmailsFollowSavedEmailOptIn(boolean emailEnabled) {
        GradeRepository grades = mock(GradeRepository.class);
        StudentRepository students = mock(StudentRepository.class);
        StudentService studentService = mock(StudentService.class);
        AuditLogRepository auditLogs = mock(AuditLogRepository.class);
        UserRepository users = mock(UserRepository.class);
        NotificationService notifications = mock(NotificationService.class);
        CourseAssessmentComponentRepository components = mock(CourseAssessmentComponentRepository.class);
        GradeAssessmentMarkRepository marks = mock(GradeAssessmentMarkRepository.class);
        EmailService emailService = mock(EmailService.class);

        User administrator = new User();
        administrator.setId(31L);
        when(users.findByUsernameNormalized(anyString())).thenReturn(Optional.of(administrator));

        User studentUser = new User();
        studentUser.setId(42L);
        Student student = new Student();
        student.setId(14L);
        student.setUser(studentUser);
        student.setEmail("student@example.com");
        student.setFullName("Test Student");
        student.setCgpa(new BigDecimal("4.50"));

        Course course = new Course();
        course.setId(7L);
        course.setName("Database Systems");
        CourseAssessmentComponent component = new CourseAssessmentComponent();
        component.setId(17L);
        component.setCourse(course);
        component.setComponentCode("TOTAL");
        component.setDisplayName("Course total");
        component.setComponentType(AssessmentComponentType.COURSEWORK);
        component.setWeightPercentage(new BigDecimal("100"));
        component.setMaxScore(new BigDecimal("100"));
        component.setRequired(true);

        Grade grade = new Grade();
        grade.setId(52L);
        grade.setStudent(student);
        grade.setCourse(course);
        grade.setGradeValue("A");
        grade.setGpaScore(new BigDecimal("5.0"));
        grade.setGradingPolicyVersion("UG_5_POINT_V1");
        grade.setTotalMark(new BigDecimal("80.00"));
        grade.setRetakeRequired(false);
        grade.setStatus(GradeStatus.PENDING);

        GradeAssessmentMark mark = new GradeAssessmentMark();
        mark.setGrade(grade);
        mark.setComponent(component);
        mark.setScore(new BigDecimal("80"));

        when(components.findByCourseIdOrderByIdAsc(7L)).thenReturn(List.of(component));
        when(marks.findByGradeId(52L)).thenReturn(List.of(mark));
        when(grades.findByIdForUpdate(52L)).thenReturn(Optional.of(grade));
        when(grades.findByIdWithStudentUserAndCourse(52L)).thenReturn(Optional.of(grade));
        when(grades.save(any(Grade.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(notifications.emailNotificationsEnabled(42L)).thenReturn(emailEnabled);

        GradeApprovalService service = new GradeApprovalService(
                grades, students, studentService, auditLogs, users, notifications, null,
                components, marks, new AssessmentCalculationService(), emailService);
        service.acknowledgeAssessmentReview(52L, "admin");
        service.approve(52L, "admin");

        if (emailEnabled) {
            verify(emailService).sendGradeNotification(
                    "student@example.com", "Test Student", "Database Systems", "A");
            verify(emailService).sendCGPAUpdateNotification(
                    "student@example.com", "Test Student", "4.50");
        } else {
            verify(emailService, never()).sendGradeNotification(
                    anyString(), anyString(), anyString(), anyString());
            verify(emailService, never()).sendCGPAUpdateNotification(
                    anyString(), anyString(), anyString());
        }
    }
}
