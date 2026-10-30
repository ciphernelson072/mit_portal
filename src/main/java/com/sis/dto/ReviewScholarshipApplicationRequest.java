package com.sis.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

public class ReviewScholarshipApplicationRequest {
    @NotBlank
    @Pattern(regexp = "APPROVED|REJECTED")
    private String status;

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
