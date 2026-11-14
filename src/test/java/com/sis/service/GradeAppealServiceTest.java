package com.sis.service;

import com.sis.entity.AuditLog;
import com.sis.entity.Course;
import com.sis.entity.Grade;
import com.sis.entity.GradeAppeal;
import com.sis.entity.GradeAppealStatus;
import com.sis.entity.GradeStatus;
import com.sis.entity.NotificationCategory;
import com.sis.entity.Role;
import com.sis.entity.Student;
import com.sis.entity.Teacher;
import com.sis.entity.User;
import com.sis.exception.ResourceNotFoundException;
import com.sis.repository.AuditLogRepository;
import com.sis.repository.GradeAppealRepository;
import com.sis.repository.GradeRepository;
import com.sis.repository.TeacherRepository;
import com.sis.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GradeAppealServiceTest {
    @Test
    void submitTrimsReasonAndNotifiesAdministratorsForPublishedApprovedGrade() {
        GradeAppealRepository appeals = mock(GradeAppealRepository.class);
        GradeRepository grades = mock(GradeRepository.class);
        UserRepository users = mock(UserRepository.class);
        AuditLogRepository auditLogs = mock(AuditLogRepository.class);
        AcademicReportService reports = mock(AcademicReportService.class);
        NotificationService notifications = mock(NotificationService.class);

        User studentUser = user(7L, "student");
        Grade grade = gradeFor(studentUser);
        when(grades.findByIdForUpdate(41L)).thenReturn(Optional.of(grade));
        when(reports.isOfficiallyPublished(3L)).thenReturn(true);
        when(appeals.existsByGradeIdAndStatusIn(eq(41L), anyCollection())).thenReturn(false);
        when(appeals.saveAndFlush(any(GradeAppeal.class))).thenAnswer(invocation -> {
            GradeAppeal saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 9L);
            return saved;
        });
        when(users.findByUsernameNormalized("student")).thenReturn(Optional.of(studentUser));
        User admin = user(12L, "admin");
        when(users.findByRoleAndIsActiveTrue(Role.ADMIN)).thenReturn(List.of(admin));

        GradeAppealService service = new GradeAppealService(
                appeals, grades, mock(TeacherRepository.class), users, auditLogs, reports, notifications);
        var result = service.submit(41L, "  Please recheck the total.  ", "student");

        assertThat(result.getStatus()).isEqualTo(GradeAppealStatus.OPEN.name());
        assertThat(result.getReason()).isEqualTo("Please recheck the total.");
        verify(auditLogs).save(any(AuditLog.class));
        verify(notifications).create(eq(12L), eq("Student grade appeal submitted"), anyString(),
                eq(NotificationCategory.GRADE));
    }

    @Test
    void rejectsAnotherStudentsGradeWithoutCreatingAnAppeal() {
        GradeAppealRepository appeals = mock(GradeAppealRepository.class);
        GradeRepository grades = mock(GradeRepository.class);
        UserRepository users = mock(UserRepository.class);
        Grade grade = gradeFor(user(8L, "another-student"));
        when(grades.findByIdForUpdate(41L)).thenReturn(Optional.of(grade));
        GradeAppealService service = new GradeAppealService(
                appeals, grades, mock(TeacherRepository.class), users, mock(AuditLogRepository.class),
                mock(AcademicReportService.class), mock(NotificationService.class));

        assertThatThrownBy(() -> service.submit(41L, "Please recheck.", "student"))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(appeals, never()).saveAndFlush(any(GradeAppeal.class));
    }

    @Test
    void rejectsDuplicateActiveAppealBeforeSaving() {
        GradeAppealRepository appeals = mock(GradeAppealRepository.class);
        GradeRepository grades = mock(GradeRepository.class);
        UserRepository users = mock(UserRepository.class);
        User studentUser = user(7L, "student");
        Grade grade = gradeFor(studentUser);
        when(grades.findByIdForUpdate(41L)).thenReturn(Optional.of(grade));
        when(appeals.existsByGradeIdAndStatusIn(eq(41L), anyCollection())).thenReturn(true);
        AcademicReportService reports = mock(AcademicReportService.class);
        when(reports.isOfficiallyPublished(3L)).thenReturn(true);
        GradeAppealService service = new GradeAppealService(
                appeals, grades, mock(TeacherRepository.class), users, mock(AuditLogRepository.class),
                reports, mock(NotificationService.class));

        assertThatThrownBy(() -> service.submit(41L, "Please recheck.", "student"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already exists");

        verify(appeals, never()).saveAndFlush(any(GradeAppeal.class));
    }

    @Test
    void completesTeacherConsultationAndRecordsDecision() {
        GradeAppealRepository appeals = mock(GradeAppealRepository.class);
        GradeRepository grades = mock(GradeRepository.class);
        UserRepository users = mock(UserRepository.class);
        NotificationService notifications = mock(NotificationService.class);
        User studentUser = user(7L, "student");
        User admin = user(12L, "admin");
        User teacherUser = user(13L, "teacher");

        Student student = mock(Student.class);
        when(student.getId()).thenReturn(3L);
        when(student.getUser()).thenReturn(studentUser);
        when(student.getFullName()).thenReturn("Student Name");
        when(student.getRegistrationNumber()).thenReturn("MIT-003");
        Teacher teacher = mock(Teacher.class);
        when(teacher.getUser()).thenReturn(teacherUser);
        Course course = mock(Course.class);
        when(course.getCourseCode()).thenReturn("CSC201");
        when(course.getName()).thenReturn("Data Structures");
        when(course.getTeacher()).thenReturn(teacher);
        Grade grade = mock(Grade.class);
        when(grade.getId()).thenReturn(41L);
        when(grade.getStudent()).thenReturn(student);
        when(grade.getCourse()).thenReturn(course);
        when(grade.getStatus()).thenReturn(GradeStatus.APPROVED);
        GradeAppeal appeal = new GradeAppeal(grade, "Please recheck the total.", LocalDateTime.now());
        ReflectionTestUtils.setField(appeal, "id", 9L);

        when(appeals.findByIdForUpdate(9L)).thenReturn(Optional.of(appeal));
        when(users.findByUsernameNormalized("admin")).thenReturn(Optional.of(admin));
        when(users.findByUsernameNormalized("teacher")).thenReturn(Optional.of(teacherUser));
        when(users.findByRoleAndIsActiveTrue(Role.ADMIN)).thenReturn(List.of(admin));
        GradeAppealService service = new GradeAppealService(
                appeals, grades, mock(TeacherRepository.class), users,
                mock(AuditLogRepository.class), mock(AcademicReportService.class), notifications);

        assertThat(service.requestTeacherConsultation(9L, "admin").getStatus())
                .isEqualTo(GradeAppealStatus.TEACHER_REVIEW.name());
        assertThat(service.respondAsTeacher(9L, "Assessment records were checked.", "teacher").getStatus())
                .isEqualTo(GradeAppealStatus.READY_FOR_DECISION.name());
        var result = service.decide(9L, "approved", "The assessment was reviewed.", "admin");

        assertThat(result.getStatus()).isEqualTo(GradeAppealStatus.APPROVED.name());
        assertThat(result.getDecisionNote()).isEqualTo("The assessment was reviewed.");
        verify(notifications).create(eq(13L), eq("Student grade appeal requires consultation"), anyString(),
                eq(NotificationCategory.GRADE));
        verify(notifications).create(eq(7L), eq("Grade appeal decision"), anyString(),
                eq(NotificationCategory.GRADE));
        verify(grades, never()).save(any(Grade.class));
    }

    private Grade gradeFor(User studentUser) {
        Student student = mock(Student.class);
        when(student.getId()).thenReturn(3L);
        when(student.getUser()).thenReturn(studentUser);
        when(student.getFullName()).thenReturn("Student Name");
        when(student.getRegistrationNumber()).thenReturn("MIT-003");

        Course course = mock(Course.class);
        when(course.getCourseCode()).thenReturn("CSC201");
        when(course.getName()).thenReturn("Data Structures");

        Grade grade = mock(Grade.class);
        when(grade.getStudent()).thenReturn(student);
        when(grade.getCourse()).thenReturn(course);
        when(grade.getStatus()).thenReturn(GradeStatus.APPROVED);
        return grade;
    }

    private User user(Long id, String username) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        when(user.getUsername()).thenReturn(username);
        return user;
    }
}
