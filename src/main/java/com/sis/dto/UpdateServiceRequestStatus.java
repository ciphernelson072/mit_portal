package com.sis.dto;

import jakarta.validation.constraints.NotBlank;

public class UpdateServiceRequestStatus {
    @NotBlank
    private String status;

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}