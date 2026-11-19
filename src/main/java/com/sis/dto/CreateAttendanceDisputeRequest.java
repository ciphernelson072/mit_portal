package com.sis.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class CreateAttendanceDisputeRequest {
    @NotNull
    private Long attendanceId;

    @NotBlank
    @Size(max = 2000)
    private String reason;

    @NotNull
    private Boolean requestedPresent;

    public Long getAttendanceId() { return attendanceId; }
    public void setAttendanceId(Long attendanceId) { this.attendanceId = attendanceId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Boolean getRequestedPresent() { return requestedPresent; }
    public void setRequestedPresent(Boolean requestedPresent) { this.requestedPresent = requestedPresent; }
}
