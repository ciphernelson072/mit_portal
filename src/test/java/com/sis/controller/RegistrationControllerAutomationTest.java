package com.sis.controller;

import com.sis.dto.AutomaticCourseAssignmentSummary;
import com.sis.dto.CreateCourseSectionRequest;
import com.sis.entity.AcademicTerm;
import com.sis.entity.Course;
import com.sis.entity.CourseRegistration;
import com.sis.entity.CourseSection;
import com.sis.entity.Student;
import com.sis.service.AutomaticCourseAssignmentService;
import com.sis.service.RegistrationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegistrationControllerAutomationTest {
    private final RegistrationService registrationService = mock(RegistrationService.class);
    private final AutomaticCourseAssignmentService automaticCourseAssignments =
            mock(AutomaticCourseAssignmentService.class);
    private final RegistrationController controller =
            new RegistrationController(registrationService, automaticCourseAssignments);

    @Test
    void creatingASectionReconcilesTermEnrollmentsAndReturnsUpdatedCapacity() {
        CreateCourseSectionRequest request = new CreateCourseSectionRequest();
        Course course = new Course();
        course.setId(8L);
        course.setCourseCode("MIT101");
        course.setName("Portal Foundations");
        course.setCredits(3);
        AcademicTerm term = new AcademicTerm();
        term.setId(12L);
        term.setName("Semester One");
        term.setRegistrationOpen(true);
        CourseSection section = new CourseSection();
        section.setId(23L);
        section.setCourse(course);
        section.setAcademicTerm(term);
        section.setSectionCode("A");
        section.setCapacity(20);

        AutomaticCourseAssignmentSummary assignments = new AutomaticCourseAssignmentSummary(
                4, 3, 1, 0, List.of());
        when(registrationService.createSection(request)).thenReturn(section);
        when(automaticCourseAssignments.assignActiveStudentsForTerm(12L)).thenReturn(assignments);
        when(registrationService.registeredCounts(List.of(section))).thenReturn(Map.of(23L, 3L));

        var response = controller.createSection(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody())
                .containsEntry("registeredCount", 3L)
                .containsEntry("seatsAvailable", 17L)
                .containsEntry("automaticAssignments", assignments);
        var callOrder = inOrder(registrationService, automaticCourseAssignments);
        callOrder.verify(registrationService).createSection(request);
        callOrder.verify(automaticCourseAssignments).assignActiveStudentsForTerm(12L);
        callOrder.verify(registrationService).registeredCounts(List.of(section));
    }

    @Test
    void creatingASectionForAClosedTermDoesNotTriggerAssignment() {
        CreateCourseSectionRequest request = new CreateCourseSectionRequest();
        Course course = new Course();
        course.setId(8L);
        course.setCourseCode("MIT101");
        course.setName("Portal Foundations");
        AcademicTerm term = new AcademicTerm();
        term.setId(12L);
        term.setName("Semester One");
        term.setRegistrationOpen(false);
        CourseSection section = new CourseSection();
        section.setId(23L);
        section.setCourse(course);
        section.setAcademicTerm(term);
        section.setSectionCode("A");
        when(registrationService.createSection(request)).thenReturn(section);
        when(registrationService.registeredCounts(List.of(section))).thenReturn(Map.of());

        var response = controller.createSection(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).doesNotContainKey("automaticAssignments");
        org.mockito.Mockito.verifyNoInteractions(automaticCourseAssignments);
    }

    @Test
    void manualStudentRegistrationRetriesRequiredAssignmentsAndReturnsTheSummary() {
        Student student = new Student();
        student.setId(19L);
        Course course = new Course();
        course.setId(8L);
        course.setCourseCode("MIT101");
        course.setName("Portal Foundations");
        AcademicTerm term = new AcademicTerm();
        term.setId(12L);
        term.setName("Semester One");
        CourseSection section = new CourseSection();
        section.setId(23L);
        section.setCourse(course);
        section.setAcademicTerm(term);
        section.setSectionCode("A");
        CourseRegistration registration = new CourseRegistration();
        registration.setId(31L);
        registration.setStudent(student);
        registration.setSection(section);
        AutomaticCourseAssignmentSummary assignments = new AutomaticCourseAssignmentSummary(
                1, 2, 0, 1, List.of());
        when(registrationService.register("student", 23L)).thenReturn(registration);
        when(automaticCourseAssignments.assignStudentForOpenTerms(19L)).thenReturn(assignments);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("student", "n/a",
                        AuthorityUtils.createAuthorityList("STUDENT")));

        try {
            var response = controller.register(23L);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody())
                    .containsEntry("courseCode", "MIT101")
                    .containsEntry("automaticAssignments", assignments);
            var callOrder = inOrder(registrationService, automaticCourseAssignments);
            callOrder.verify(registrationService).register("student", 23L);
            callOrder.verify(automaticCourseAssignments).assignStudentForOpenTerms(19L);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void droppingARegistrationRetriesMatchingRequiredCoursesWithoutChangingTheResponseContract() {
        Course course = new Course();
        course.setId(8L);
        AcademicTerm term = new AcademicTerm();
        term.setId(12L);
        CourseSection section = new CourseSection();
        section.setCourse(course);
        section.setAcademicTerm(term);
        Student student = new Student();
        student.setId(19L);
        CourseRegistration droppedRegistration = new CourseRegistration();
        droppedRegistration.setStudent(student);
        droppedRegistration.setSection(section);
        when(registrationService.drop("student", 31L)).thenReturn(droppedRegistration);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("student", "n/a",
                        AuthorityUtils.createAuthorityList("STUDENT")));

        try {
            var response = controller.drop(31L);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(response.getBody()).isNull();
            verify(automaticCourseAssignments).assignActiveStudentsForCourseInTerm(19L, 8L, 12L);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
