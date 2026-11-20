package com.sis.service;

import com.sis.entity.AdmissionApplication;
import com.sis.entity.AdmissionDocumentRequirement;
import com.sis.repository.AdmissionApplicationDocumentRepository;
import com.sis.repository.AdmissionDocumentRequirementRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdmissionDocumentServiceTest {
    @Test
    void requiresEveryActiveRequiredDocumentBeforeStoringAnApplication() throws java.io.IOException {
        AdmissionDocumentRequirementRepository requirements = mock(AdmissionDocumentRequirementRepository.class);
        AdmissionApplicationDocumentRepository documents = mock(AdmissionApplicationDocumentRepository.class);
        FileUploadService uploads = mock(FileUploadService.class);
        AdmissionDocumentRequirement transcript = new AdmissionDocumentRequirement();
        transcript.setName("Academic transcript");
        transcript.setRequired(true);
        when(requirements.findByActiveTrueOrderBySortOrderAscIdAsc()).thenReturn(List.of(transcript));

        assertThrows(IllegalArgumentException.class, () -> new AdmissionDocumentService(
                requirements, documents, uploads)
                .storeForApplication(new AdmissionApplication(), Map.of()));

        verify(uploads, never()).uploadAdmissionDocument(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsFilesThatDoNotMatchAnActiveRequirement() {
        AdmissionDocumentRequirementRepository requirements = mock(AdmissionDocumentRequirementRepository.class);
        AdmissionApplicationDocumentRepository documents = mock(AdmissionApplicationDocumentRepository.class);
        FileUploadService uploads = mock(FileUploadService.class);
        when(requirements.findByActiveTrueOrderBySortOrderAscIdAsc()).thenReturn(List.of());
        MockMultipartFile unexpected = new MockMultipartFile("document_99", "proof.pdf",
                "application/pdf", new byte[] {1});

        assertThrows(IllegalArgumentException.class, () -> new AdmissionDocumentService(
                requirements, documents, uploads)
                .storeForApplication(new AdmissionApplication(), Map.of("document_99", unexpected)));

        verify(documents, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
