package com.sis.controller;

import com.sis.service.AcademicRecordsService;
import com.sis.service.PdfDocumentService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AcademicRecordsControllerTest {
    private final AcademicRecordsService records = mock(AcademicRecordsService.class);
    private final AcademicRecordsController controller =
            new AcademicRecordsController(records, mock(PdfDocumentService.class));

    @Test
    void degreeAuditUsesExactProgrammeAndYearWhenProvided() {
        when(records.degreeAudit(42L, 7L, 2026L))
                .thenReturn(Map.of("eligibleForGraduation", true));

        Map<String, Object> response = controller.degreeAudit(42L, 7L, 2026L);

        verify(records).degreeAudit(42L, 7L, 2026L);
        org.assertj.core.api.Assertions.assertThat(response.get("eligibleForGraduation")).isEqualTo(true);
    }

    @Test
    void degreeAuditRejectsOnlyOneOfProgrammeOrYear() {
        assertThatThrownBy(() -> controller.degreeAudit(42L, 7L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("provided together");
        assertThatThrownBy(() -> controller.degreeAudit(42L, null, 2026L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("provided together");
    }
}
