package com.sis.controller;

import com.sis.entity.Assignment;
import com.sis.entity.AssignmentSubmission;
import com.sis.entity.Course;
import com.sis.entity.Student;
import com.sis.repository.AssignmentRepository;
import com.sis.repository.AssignmentSubmissionRepository;
import com.sis.repository.StudentRepository;
import com.sis.service.SchoolService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeacherAssignmentSubmissionControllerTest {

    private final AssignmentRepository assignments = mock(AssignmentRepository.class);
    private final AssignmentSubmissionRepository submissions = mock(AssignmentSubmissionRepository.class);
    private final StudentRepository students = mock(StudentRepository.class);
    private final SchoolService schoolService = mock(SchoolService.class);
    private final TeacherAssignmentSubmissionController controller =
            new TeacherAssignmentSubmissionController(assignments, submissions, students, schoolService);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void assignedTeacherCanSeeSubmissionsWithStudentNames() {
        authenticate();
        Assignment assignment = assignment(5L, 8L);
        Course course = course(8L);
        AssignmentSubmission submission = new AssignmentSubmission();
        submission.setAssignmentId(5L);
        submission.setStudentId(13L);
        submission.setContent("My response");
        submission.setSubmittedAt(LocalDateTime.of(2026, 10, 1, 10, 0));
        Student student = new Student();
        student.setId(13L);
        student.setFullName("Student Name");
        when(assignments.findById(5L)).thenReturn(Optional.of(assignment));
        when(schoolService.getCourseById(8L)).thenReturn(Optional.of(course));
        when(schoolService.teacherOwnsCourse("teacher", course)).thenReturn(true);
        when(submissions.findByAssignmentIdOrderBySubmittedAtAsc(5L)).thenReturn(List.of(submission));
        when(students.findAllById(List.of(13L))).thenReturn(List.of(student));

        var response = controller.listSubmissions(5L);

        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).getStudentName()).isEqualTo("Student Name");
        assertThat(response.getBody().get(0).getContent()).isEqualTo("My response");
    }

    @Test
    void teacherCannotReadSubmissionsForAnotherTeachersCourse() {
        authenticate();
        Assignment assignment = assignment(5L, 8L);
        Course course = course(8L);
        when(assignments.findById(5L)).thenReturn(Optional.of(assignment));
        when(schoolService.getCourseById(8L)).thenReturn(Optional.of(course));
        when(schoolService.teacherOwnsCourse("teacher", course)).thenReturn(false);

        var response = controller.listSubmissions(5L);

        assertThat(response.getStatusCodeValue()).isEqualTo(403);
        verify(submissions, never()).findByAssignmentIdOrderBySubmittedAtAsc(5L);
    }

    private static void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("teacher", "n/a",
                        AuthorityUtils.createAuthorityList("TEACHER")));
    }

    private static Assignment assignment(Long id, Long courseId) {
        Assignment assignment = new Assignment();
        ReflectionTestUtils.setField(assignment, "id", id);
        assignment.setCourseId(courseId);
        return assignment;
    }

    private static Course course(Long id) {
        Course course = new Course();
        course.setId(id);
        return course;
    }
}
