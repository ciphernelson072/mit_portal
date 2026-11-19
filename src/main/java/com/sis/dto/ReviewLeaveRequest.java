package com.sis.dto;

import com.sis.entity.LeaveRequestStatus;
import jakarta.validation.constraints.NotNull;

public class ReviewLeaveRequest {
    @NotNull private LeaveRequestStatus status;
    public LeaveRequestStatus getStatus() { return status; }
    public void setStatus(LeaveRequestStatus status) { this.status = status; }
}