package com.sis.security;

import com.sis.entity.Role;
import com.sis.entity.Teacher;
import com.sis.entity.User;
import com.sis.repository.CourseRegistrationRepository;
import com.sis.repository.CourseRepository;
import com.sis.repository.TeacherRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TeacherAccessServiceTest {
    private final TeacherRepository teachers = mock(TeacherRepository.class);
    private final CourseRepository courses = mock(CourseRepository.class);
    private final CourseRegistrationRepository registrations = mock(CourseRegistrationRepository.class);
    private final TeacherAccessService service = new TeacherAccessService(teachers, courses, registrations);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void teacherCanAccessAttendanceOnlyForTheirEnrolledCourseStudent() {
        authenticate("teacher", "TEACHER");
        Teacher teacher = new Teacher(new User("teacher", "encoded", Role.TEACHER), "Teacher");
        when(teachers.findByUserUsername("teacher")).thenReturn(Optional.of(teacher));
        when(courses.existsByIdAndTeacherUserUsername(10L, "teacher")).thenReturn(true);
        when(courses.existsEnrolledStudent(10L, 42L)).thenReturn(true);

        assertTrue(service.teachesStudentInCourse(42L, 10L));

        when(courses.existsByIdAndTeacherUserUsername(11L, "teacher")).thenReturn(false);
        assertFalse(service.teachesStudentInCourse(42L, 11L));
    }

    private void authenticate(String username, String authority) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "n/a",
                        AuthorityUtils.createAuthorityList(authority)));
    }
}
