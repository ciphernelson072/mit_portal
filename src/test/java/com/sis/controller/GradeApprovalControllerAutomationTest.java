package com.sis.controller;

import com.sis.dto.AutomaticCourseAssignmentSummary;
import com.sis.dto.GradeDto;
import com.sis.entity.Grade;
import com.sis.entity.Student;
import com.sis.repository.GradeRepository;
import com.sis.service.AutomaticCourseAssignmentService;
import com.sis.service.GradeApprovalService;
import com.sis.service.SchoolService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GradeApprovalControllerAutomationTest {
    private final GradeApprovalService gradeApprovalService = mock(GradeApprovalService.class);
    private final SchoolService schoolService = mock(SchoolService.class);
    private final GradeRepository gradeRepository = mock(GradeRepository.class);
    private final AutomaticCourseAssignmentService automaticCourseAssignments =
            mock(AutomaticCourseAssignmentService.class);
    private final GradeApprovalController controller = new GradeApprovalController(
            gradeApprovalService, schoolService, gradeRepository, automaticCourseAssignments);

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void approvingPrerequisiteGradeRetriesAssignmentsAndReturnsTheSummary() {
        Student student = new Student();
        student.setId(19L);
        Grade grade = new Grade();
        grade.setId(44L);
        grade.setStudent(student);
        GradeDto gradeDto = new GradeDto();
        AutomaticCourseAssignmentSummary summary =
                new AutomaticCourseAssignmentSummary(1, 2, 0, 0, List.of());
        when(gradeApprovalService.approve(44L, "admin")).thenReturn(grade);
        when(schoolService.toGradeDto(grade)).thenReturn(gradeDto);
        when(automaticCourseAssignments.assignStudentForOpenTerms(19L)).thenReturn(summary);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", "n/a",
                        AuthorityUtils.createAuthorityList("ADMIN")));

        var response = controller.approve(44L);

        assertThat(response.getBody()).isSameAs(gradeDto);
        assertThat(response.getBody().getAutomaticAssignments()).isEqualTo(summary);
        var callOrder = inOrder(gradeApprovalService, automaticCourseAssignments);
        callOrder.verify(gradeApprovalService).approve(44L, "admin");
        callOrder.verify(automaticCourseAssignments).assignStudentForOpenTerms(19L);
    }
}
