package com.sis.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class ReviewAttendanceDisputeRequest {
    @NotBlank
    @Pattern(regexp = "APPROVED|REJECTED|ESCALATED")
    private String status;

    @NotBlank
    @Size(max = 2000)
    private String resolution;

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }
}
