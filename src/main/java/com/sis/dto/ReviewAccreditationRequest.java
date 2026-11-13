package com.sis.dto;

import com.sis.entity.AccreditationStatus;
import javax.validation.constraints.NotNull;

public class ReviewAccreditationRequest {
    @NotNull private AccreditationStatus status;
    public AccreditationStatus getStatus() { return status; }
    public void setStatus(AccreditationStatus status) { this.status = status; }
}