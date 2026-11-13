package com.sis.dto;

import com.sis.entity.ThesisSubmissionStatus;

import java.time.LocalDateTime;

public class ThesisSubmissionDto {
    private final Long id;
    private final Long researchProjectId;
    private final String notes;
    private final ThesisSubmissionStatus status;
    private final LocalDateTime submittedAt;

    public ThesisSubmissionDto(Long id, Long researchProjectId, String notes,
                               ThesisSubmissionStatus status, LocalDateTime submittedAt) {
        this.id = id;
        this.researchProjectId = researchProjectId;
        this.notes = notes;
        this.status = status;
        this.submittedAt = submittedAt;
    }

    public Long getId() { return id; }
    public Long getResearchProjectId() { return researchProjectId; }
    public String getNotes() { return notes; }
    public ThesisSubmissionStatus getStatus() { return status; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
}
