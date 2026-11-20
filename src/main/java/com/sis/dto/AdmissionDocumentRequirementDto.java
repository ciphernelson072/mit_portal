package com.sis.dto;

public record AdmissionDocumentRequirementDto(
        Long id,
        String name,
        String description,
        boolean required,
        boolean active,
        int sortOrder) {
}
