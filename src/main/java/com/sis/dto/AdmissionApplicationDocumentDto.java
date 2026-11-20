package com.sis.dto;

import java.time.LocalDateTime;

public record AdmissionApplicationDocumentDto(
        Long id,
        Long requirementId,
        String requirementName,
        boolean required,
        LocalDateTime uploadedAt) {
}
