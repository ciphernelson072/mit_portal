package com.sis.controller;

import com.sis.dto.AutomaticCourseAssignmentSummary;
import com.sis.service.AutomaticCourseAssignmentService;
import com.sis.service.FinancialHoldService;
import com.sis.service.SchoolService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FinancialHoldControllerAutomationTest {
    private final FinancialHoldService holdService = mock(FinancialHoldService.class);
    private final AutomaticCourseAssignmentService automaticCourseAssignments =
            mock(AutomaticCourseAssignmentService.class);
    private final FinancialHoldController controller = new FinancialHoldController(
            holdService, mock(SchoolService.class), automaticCourseAssignments);

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void releasingTheFinalHoldRetriesAssignmentsAndReturnsTheSummary() {
        authenticate();
        when(holdService.release(4L, "admin")).thenReturn(19L);
        when(holdService.hasActiveHold(19L)).thenReturn(false);
        AutomaticCourseAssignmentSummary summary =
                new AutomaticCourseAssignmentSummary(1, 2, 0, 0, List.of());
        when(automaticCourseAssignments.assignStudentForOpenTerms(19L)).thenReturn(summary);

        var response = controller.release(4L);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody())
                .containsEntry("automaticAssignments", summary)
                .containsEntry("message", "Final financial hold released; open-term registration was retried.");
        verify(automaticCourseAssignments).assignStudentForOpenTerms(19L);
    }

    @Test
    void releasingOneOfSeveralHoldsDoesNotRetryAssignments() {
        authenticate();
        when(holdService.release(4L, "admin")).thenReturn(19L);
        when(holdService.hasActiveHold(19L)).thenReturn(true);

        var response = controller.release(4L);

        assertThat(response.getBody())
                .containsEntry("message",
                        "Financial hold released; additional active holds remain, so registration was not retried.")
                .doesNotContainKey("automaticAssignments");
        verify(automaticCourseAssignments, never()).assignStudentForOpenTerms(19L);
    }

    private void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", "n/a",
                        AuthorityUtils.createAuthorityList("ADMIN")));
    }
}
