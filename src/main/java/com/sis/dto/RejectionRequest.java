package com.sis.dto;

import jakarta.validation.constraints.Size;

public class RejectionRequest {
    @Size(max = 1000)
    private String reason = "Rejected by administrator";

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
