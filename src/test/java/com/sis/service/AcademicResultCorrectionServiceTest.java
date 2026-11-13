package com.sis.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sis.entity.*;
import com.sis.repository.*;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AcademicResultCorrectionServiceTest {

    private final GradeRepository grades = mock(GradeRepository.class);
    private final GradeCorrectionRequestRepository corrections = mock(GradeCorrectionRequestRepository.class);
    private final GradeAssessmentMarkRepository marks = mock(GradeAssessmentMarkRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final AuditLogRepository audits = mock(AuditLogRepository.class);
    private final AcademicResultCorrectionService service = new AcademicResultCorrectionService(
            grades, corrections, marks, users, audits, new ObjectMapper());

    @Test
    void authorizationCapturesPreviousResultAndRequiresApprovedGrade() {
        Grade grade = approvedGrade(12L);
        User admin = new User();
        admin.setId(4L);
        when(grades.findByIdForUpdate(12L)).thenReturn(Optional.of(grade));
        when(corrections.existsByGradeIdAndStatusIn(eq(12L), anyCollection())).thenReturn(false);
        when(users.findByUsernameNormalized("admin")).thenReturn(Optional.of(admin));
        when(corrections.save(any(GradeCorrectionRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.authorize(12L, "Corrected assessment entry", "admin");

        assertEquals("AUTHORIZED", result.getStatus());
        assertEquals("Corrected assessment entry", result.getReason());
        assertTrue(result.getPreviousSnapshot().contains("\"gradeValue\":\"B\""));
        assertTrue(result.getPreviousSnapshot().contains("\"gpaScore\":3.0"));
        verify(audits).save(argThat(log -> "GRADE_CORRECTION_AUTHORIZED".equals(log.getAction())));

        grade.setStatus(GradeStatus.PENDING);
        assertThrows(IllegalStateException.class,
                () -> service.authorize(12L, "Reason", "admin"));
    }

    @Test
    void firstCorrectionEditInvalidatesApprovalAndMovesRequestToProgress() {
        Grade grade = approvedGrade(13L);
        GradeCorrectionRequest correction = new GradeCorrectionRequest(
                grade, "Approved correction reason", "{}", "admin", java.time.LocalDateTime.now());
        when(grades.findByIdForUpdate(13L)).thenReturn(Optional.of(grade));
        when(corrections.findTopByGradeIdAndStatusInOrderByIdDesc(eq(13L), anyCollection()))
                .thenReturn(Optional.of(correction));
        when(grades.save(grade)).thenReturn(grade);
        User actor = new User();
        actor.setId(5L);
        when(users.findByUsernameNormalized("teacher")).thenReturn(Optional.of(actor));

        Grade editable = service.beginAuthorizedCorrection(13L, "teacher");

        assertSame(grade, editable);
        assertEquals(GradeStatus.PENDING, grade.getStatus());
        assertNull(grade.getApprovedBy());
        assertNull(grade.getApprovedAt());
        assertEquals(GradeCorrectionStatus.IN_PROGRESS, correction.getStatus());
        verify(grades).save(grade);
    }

    @Test
    void approvedGradeCannotBeEditedWithoutAnAuthorizedCorrection() {
        Grade grade = approvedGrade(14L);
        when(grades.findByIdForUpdate(14L)).thenReturn(Optional.of(grade));
        when(corrections.findTopByGradeIdAndStatusInOrderByIdDesc(eq(14L), anyCollection()))
                .thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class,
                () -> service.beginAuthorizedCorrection(14L, "teacher"));
        assertEquals(GradeStatus.APPROVED, grade.getStatus());
        verify(grades, never()).save(any());
    }

    @Test
    void correctionAuthorizationCannotBeDuplicatedWhileActive() {
        Grade grade = approvedGrade(15L);
        when(grades.findByIdForUpdate(15L)).thenReturn(Optional.of(grade));
        when(corrections.existsByGradeIdAndStatusIn(eq(15L), anyCollection())).thenReturn(true);

        assertThrows(IllegalStateException.class,
                () -> service.authorize(15L, "Another correction", "admin"));
        verify(corrections, never()).save(any());
    }

    private Grade approvedGrade(Long id) {
        Grade grade = new Grade();
        grade.setId(id);
        grade.setGradeValue("B");
        grade.setGpaScore(new BigDecimal("3.00"));
        grade.setTotalMark(new BigDecimal("80.00"));
        grade.setRemarks("Original approved result");
        grade.setStatus(GradeStatus.APPROVED);
        grade.setApprovedBy("admin");
        grade.setApprovedAt(java.time.LocalDateTime.now());
        return grade;
    }
}
