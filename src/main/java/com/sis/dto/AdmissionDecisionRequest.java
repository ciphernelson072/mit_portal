package com.sis.dto;

import com.sis.entity.AdmissionApplicationStatus;
import javax.validation.constraints.NotNull;

public class AdmissionDecisionRequest {
    @NotNull private AdmissionApplicationStatus status;
    private String reviewerNotes;

    public AdmissionApplicationStatus getStatus() { return status; }
    public void setStatus(AdmissionApplicationStatus status) { this.status = status; }
    public String getReviewerNotes() { return reviewerNotes; }
    public void setReviewerNotes(String reviewerNotes) { this.reviewerNotes = reviewerNotes; }
}