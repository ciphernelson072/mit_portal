package com.sis.dto;

import com.sis.entity.ThesisSubmissionStatus;
import javax.validation.constraints.NotNull;

public class ReviewThesisSubmissionRequest {
    @NotNull private ThesisSubmissionStatus status;
    public ThesisSubmissionStatus getStatus() { return status; }
    public void setStatus(ThesisSubmissionStatus status) { this.status = status; }
}