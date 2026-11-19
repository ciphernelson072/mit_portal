package com.sis.controller;

import com.sis.dto.CourseDto;
import com.sis.entity.Course;
import com.sis.entity.GradeCorrectionStatus;
import com.sis.entity.Teacher;
import com.sis.service.AcademicResultCorrectionService;
import com.sis.service.CourseAssessmentService;
import com.sis.service.SchoolService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CourseAssessmentControllerTest {

    private final CourseAssessmentService assessments = mock(CourseAssessmentService.class);
    private final SchoolService school = mock(SchoolService.class);
    private final AcademicResultCorrectionService corrections = mock(AcademicResultCorrectionService.class);
    private final CourseAssessmentController controller =
            new CourseAssessmentController(assessments, school, corrections);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void administratorCanLoadCorrectionsWithoutTeacherProfile() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", "n/a",
                        AuthorityUtils.createAuthorityList("ADMIN")));
        Course firstCourse = new Course();
        firstCourse.setId(10L);
        Course secondCourse = new Course();
        secondCourse.setId(11L);
        when(school.listCourses()).thenReturn(List.of(firstCourse, secondCourse));

        var response = controller.teacherCorrections();

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(corrections).listForCourses(List.of(10L, 11L),
                Arrays.asList(GradeCorrectionStatus.values()));
    }

    @Test
    void teacherCorrectionsRemainScopedToAssignedCourses() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("teacher", "n/a",
                        AuthorityUtils.createAuthorityList("TEACHER")));
        var teacher = new Teacher();
        teacher.setId(7L);
        when(school.getTeacherByUsername("teacher")).thenReturn(Optional.of(teacher));
        CourseDto course = new CourseDto();
        course.setId(10L);
        when(school.getCoursesForTeacher(7L)).thenReturn(List.of(course));

        var response = controller.teacherCorrections();

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(corrections).listForCourses(List.of(10L), Arrays.asList(GradeCorrectionStatus.values()));
    }
}
