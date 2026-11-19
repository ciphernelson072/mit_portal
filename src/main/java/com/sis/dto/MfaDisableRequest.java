package com.sis.dto;

import jakarta.validation.constraints.NotBlank;

public class MfaDisableRequest {

    @NotBlank(message = "Current password is required")
    private String currentPassword;

    private String otp;

    public String getCurrentPassword() {
        return currentPassword;
    }

    public void setCurrentPassword(String currentPassword) {
        this.currentPassword = currentPassword;
    }

    public String getOtp() {
        return otp;
    }

    public void setOtp(String otp) {
        this.otp = otp;
    }
}
