package com.sis.service;

import com.sis.entity.Course;
import com.sis.entity.Grade;
import com.sis.entity.Student;
import com.sis.entity.User;
import com.sis.repository.AuditLogRepository;
import com.sis.repository.UserRepository;
import com.sis.entity.AuditLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GradeImportServiceTest {

    private SchoolService schoolService;
    private AuditLogRepository auditLogRepository;
    private UserRepository userRepository;
    private GradeImportService service;

    @BeforeEach
    void setUp() {
        schoolService = mock(SchoolService.class);
        auditLogRepository = mock(AuditLogRepository.class);
        userRepository = mock(UserRepository.class);
        User actor = new User();
        actor.setId(99L);
        when(userRepository.findByUsernameNormalized("admin-user")).thenReturn(Optional.of(actor));
        service = new GradeImportService(schoolService, auditLogRepository, userRepository);
    }

    @Test
    void importsQuotedCsvFieldsWithoutLosingCommas() {
        Student student = new Student();
        student.setId(1L);
        student.setFullName("Amina Student");
        User user = new User();
        user.setUsername("amina");
        student.setUser(user);

        Course course = new Course();
        course.setId(2L);
        course.setName("Mathematics");

        Grade grade = new Grade(student, course, "A", "Outstanding, consistent progress");
        grade.setId(3L);
        when(schoolService.getStudentById(1L)).thenReturn(Optional.of(student));
        when(schoolService.getCourseById(2L)).thenReturn(Optional.of(course));
        when(schoolService.studentEnrolledInCourse(1L, 2L)).thenReturn(true);
        when(schoolService.saveGrade(student, course, "A", "Outstanding, consistent progress")).thenReturn(grade);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "grades.csv",
                "text/csv",
                ("student_id,course_id,grade_value,remarks\n"
                        + "1,2,A,\"Outstanding, consistent progress\"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));

        var imported = service.importGrades(file, "admin-user");

        assertEquals(1, imported.size());
        assertEquals("Outstanding, consistent progress", imported.get(0).getRemarks());
        org.mockito.ArgumentCaptor<AuditLog> audit = org.mockito.ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(audit.capture());
        assertEquals(99L, audit.getValue().getUserId());
    }

    @Test
    void importsExcelCompatibleCsvTemplateWithBomAndMultilineRemarks() {
        Student student = new Student();
        student.setId(1L);
        User user = new User();
        user.setUsername("amina");
        student.setUser(user);

        Course course = new Course();
        course.setId(2L);
        course.setName("Mathematics");

        String remarks = "Outstanding progress,\ncontinues to improve";
        Grade grade = new Grade(student, course, "A", remarks);
        grade.setId(3L);
        when(schoolService.getStudentById(1L)).thenReturn(Optional.of(student));
        when(schoolService.getCourseById(2L)).thenReturn(Optional.of(course));
        when(schoolService.studentEnrolledInCourse(1L, 2L)).thenReturn(true);
        when(schoolService.saveGrade(student, course, "A", remarks)).thenReturn(grade);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "grades.csv",
                "text/csv",
                ("\uFEFFstudentId,courseId,gradeValue,gpaScore,remarks\r\n"
                        + "1,2,A,,\"Outstanding progress,\ncontinues to improve\"\r\n")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));

        var imported = service.importGrades(file, "admin-user");

        assertEquals(1, imported.size());
        assertEquals(remarks, imported.get(0).getRemarks());
    }

    @Test
    void rejectsCsvWithoutRequiredColumnsBeforeImportingRows() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "grades.csv",
                "text/csv",
                "student_id,course_id,remarks\n1,2,Missing grade\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThrows(IllegalArgumentException.class, () -> service.importGrades(file, "admin-user"));
        verify(schoolService, never()).saveGrade(any(), any(), any(), any());
    }

    @Test
    void rejectsUnclosedQuotedCsvField() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "grades.csv",
                "text/csv",
                ("student_id,course_id,grade_value,remarks\n"
                        + "1,2,A,\"Unclosed remark\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThrows(IllegalArgumentException.class, () -> service.importGrades(file, "admin-user"));
        verify(schoolService, never()).saveGrade(any(), any(), any(), any());
    }

    @Test
    void rejectsCsvWithHeaderButNoDataRows() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "grades.csv", "text/csv",
                "student_id,course_id,grade_value,remarks\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThrows(IllegalArgumentException.class, () -> service.importGrades(file, "admin-user"));
        verify(schoolService, never()).saveGrade(any(), any(), any(), any());
    }

    @Test
    void rejectsStudentNotEnrolledInImportedCourse() {
        Student student = new Student();
        student.setId(1L);
        Course course = new Course();
        course.setId(2L);
        when(schoolService.getStudentById(1L)).thenReturn(Optional.of(student));
        when(schoolService.getCourseById(2L)).thenReturn(Optional.of(course));
        when(schoolService.studentEnrolledInCourse(1L, 2L)).thenReturn(false);
        MockMultipartFile file = new MockMultipartFile(
                "file", "grades.csv", "text/csv",
                "student_id,course_id,grade_value,remarks\n1,2,A,Reviewed\n"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThrows(IllegalArgumentException.class, () -> service.importGrades(file, "admin-user"));
        verify(schoolService, never()).saveGrade(any(), any(), any(), any());
    }

    @Test
    void rejectsDuplicateStudentCourseRows() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "grades.csv", "text/csv",
                ("student_id,course_id,grade_value,remarks\n"
                        + "1,2,A,First\n"
                        + "1,2,B,Duplicate\n")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThrows(IllegalArgumentException.class, () -> service.importGrades(file, "admin-user"));
        verify(schoolService, never()).saveGrade(any(), any(), any(), any());
    }
}
