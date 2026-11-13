package com.sis.dto;

import com.sis.entity.ProcurementRequestStatus;
import javax.validation.constraints.NotNull;

public class ReviewProcurementRequest {
    @NotNull private ProcurementRequestStatus status;
    public ProcurementRequestStatus getStatus() { return status; }
    public void setStatus(ProcurementRequestStatus status) { this.status = status; }
}