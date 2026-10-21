package com.sis.service;

import com.sis.dto.CreatePrerequisiteRequest;
import com.sis.entity.Course;
import com.sis.repository.CoursePrerequisiteRepository;
import com.sis.repository.CourseRepository;
import com.sis.repository.CurriculumCourseRepository;
import com.sis.repository.ProgramRepository;
import com.sis.repository.StudentProgramRepository;
import com.sis.repository.StudentRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CurriculumServiceTest {

    @Test
    void rejectsIndirectPrerequisiteCycles() {
        CurriculumCourseRepository curriculum = mock(CurriculumCourseRepository.class);
        CoursePrerequisiteRepository prerequisites = mock(CoursePrerequisiteRepository.class);
        ProgramRepository programs = mock(ProgramRepository.class);
        CourseRepository courses = mock(CourseRepository.class);
        StudentRepository students = mock(StudentRepository.class);
        StudentProgramRepository studentPrograms = mock(StudentProgramRepository.class);

        Course courseThree = course(3L);
        Course courseOne = course(1L);
        when(courses.findById(3L)).thenReturn(Optional.of(courseThree));
        when(courses.findById(1L)).thenReturn(Optional.of(courseOne));
        when(prerequisites.findPrerequisiteCourseIdsByCourseId(1L)).thenReturn(List.of(2L));
        when(prerequisites.findPrerequisiteCourseIdsByCourseId(2L)).thenReturn(List.of(3L));

        CurriculumService service = new CurriculumService(
                curriculum, prerequisites, programs, courses, students, studentPrograms);
        CreatePrerequisiteRequest request = new CreatePrerequisiteRequest();
        request.setCourseId(3L);
        request.setPrerequisiteCourseId(1L);

        assertThatThrownBy(() -> service.addPrerequisite(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Course prerequisite would create a cycle");
        verify(prerequisites, never()).save(any());
    }

    private Course course(Long id) {
        Course course = new Course();
        course.setId(id);
        return course;
    }
}
