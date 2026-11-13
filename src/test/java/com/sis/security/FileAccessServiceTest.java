package com.sis.security;

import com.sis.repository.CourseRegistrationRepository;
import com.sis.repository.CourseRepository;
import com.sis.repository.TeacherRepository;
import com.sis.entity.Teacher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class FileAccessServiceTest {

    private final StudentAccessService studentAccessService = mock(StudentAccessService.class);
    private final TeacherRepository teacherRepository = mock(TeacherRepository.class);
    private final CourseRepository courseRepository = mock(CourseRepository.class);
    private final CourseRegistrationRepository registrations = mock(CourseRegistrationRepository.class);
    private final FileAccessService fileAccessService = new FileAccessService(
            studentAccessService,
            new TeacherAccessService(teacherRepository, courseRepository, registrations));

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void teacherCanViewPhotoOfStudentTheyTeach() {
        authenticateTeacher();
        when(courseRepository.existsAssignedStudent(3L, 7L)).thenReturn(true);

        assertTrue(fileAccessService.isOwnFile("students/7_abc.jpg"));
        verify(courseRepository).existsAssignedStudent(3L, 7L);
    }

    @Test
    void teacherCannotViewPhotoOfStudentOutsideTheirCourses() {
        authenticateTeacher();
        when(courseRepository.existsAssignedStudent(3L, 8L)).thenReturn(false);
        when(registrations.existsBySectionCourseTeacherIdAndStudentIdAndStatus(anyLong(), anyLong(), any()))
                .thenReturn(false);

        assertFalse(fileAccessService.isOwnFile("students/8_abc.jpg"));
    }

    private void authenticateTeacher() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("teacher1", "n/a", List.of(() -> "TEACHER")));
        Teacher teacher = mock(Teacher.class);
        when(teacher.getId()).thenReturn(3L);
        when(teacherRepository.findByUserUsername("teacher1")).thenReturn(java.util.Optional.of(teacher));
    }
}
