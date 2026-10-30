package com.sis.controller;

import com.sis.dto.AssignmentSubmissionRequest;
import com.sis.entity.Assignment;
import com.sis.entity.AssignmentSubmission;
import com.sis.entity.Role;
import com.sis.entity.Student;
import com.sis.entity.User;
import com.sis.repository.*;
import com.sis.service.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PortalAssignmentSubmissionTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void newSubmissionReturnsCreatedAndResubmissionReturnsOk() {
        SchoolService schoolService = mock(SchoolService.class);
        AssignmentRepository assignments = mock(AssignmentRepository.class);
        AssignmentSubmissionRepository submissions = mock(AssignmentSubmissionRepository.class);
        PortalController controller = controller(schoolService, assignments, submissions);
        Student student = new Student();
        student.setId(17L);
        User user = new User("student", "n/a", Role.STUDENT);
        Assignment assignment = new Assignment();
        assignment.setCourseId(23L);
        AssignmentSubmission existing = new AssignmentSubmission();
        authenticate();
        when(schoolService.getUserByUsername("student")).thenReturn(Optional.of(user));
        when(schoolService.getStudentByUsername("student")).thenReturn(Optional.of(student));
        when(schoolService.studentEnrolledInCourse(17L, 23L)).thenReturn(true);
        when(assignments.findById(5L)).thenReturn(Optional.of(assignment));
        when(submissions.findByAssignmentIdAndStudentId(5L, 17L))
                .thenReturn(Optional.empty(), Optional.of(existing));
        when(submissions.save(any(AssignmentSubmission.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        AssignmentSubmissionRequest request = new AssignmentSubmissionRequest();
        request.setContent("My response");

        var created = controller.submit(5L, request);
        var updated = controller.submit(5L, request);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody()).isSameAs(existing);
        assertThat(updated.getBody().getContent()).isEqualTo("My response");
    }

    @Test
    void studentCannotSubmitForCourseTheyAreNotEnrolledIn() {
        SchoolService schoolService = mock(SchoolService.class);
        AssignmentRepository assignments = mock(AssignmentRepository.class);
        AssignmentSubmissionRepository submissions = mock(AssignmentSubmissionRepository.class);
        PortalController controller = controller(schoolService, assignments, submissions);
        Student student = new Student();
        student.setId(17L);
        User user = new User("student", "n/a", Role.STUDENT);
        Assignment assignment = new Assignment();
        assignment.setCourseId(23L);
        authenticate();
        when(schoolService.getUserByUsername("student")).thenReturn(Optional.of(user));
        when(schoolService.getStudentByUsername("student")).thenReturn(Optional.of(student));
        when(schoolService.studentEnrolledInCourse(17L, 23L)).thenReturn(false);
        when(assignments.findById(5L)).thenReturn(Optional.of(assignment));

        var response = controller.submit(5L, new AssignmentSubmissionRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(submissions, never()).save(any(AssignmentSubmission.class));
    }

    private static void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("student", "n/a",
                        AuthorityUtils.createAuthorityList("STUDENT")));
    }

    private static PortalController controller(SchoolService schoolService,
                                               AssignmentRepository assignments,
                                               AssignmentSubmissionRepository submissions) {
        return new PortalController(
                schoolService, assignments, submissions,
                mock(ExamRepository.class), mock(ScholarshipRepository.class),
                mock(ScholarshipApplicationRepository.class), mock(ScholarshipService.class),
                mock(UserPreferenceRepository.class), mock(SystemSettingRepository.class),
                mock(AuditLogRepository.class), mock(PaymentTransactionRepository.class),
                mock(CampusEventRepository.class), mock(TransportRouteRepository.class),
                mock(EventRegistrationRepository.class), mock(ClubRepository.class),
                mock(ClubMembershipRepository.class), mock(NotificationService.class),
                mock(EventRegistrationService.class));
    }
}
