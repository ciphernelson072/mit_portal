package com.sis.dto;

import jakarta.validation.constraints.NotBlank;

public class MfaVerifyRequest {

    @NotBlank(message = "OTP is required")
    private String otp;

    @NotBlank(message = "Current password is required")
    private String currentPassword;

    public MfaVerifyRequest() {
    }

    public String getOtp() {
        return otp;
    }

    public void setOtp(String otp) {
        this.otp = otp;
    }

    public String getCurrentPassword() {
        return currentPassword;
    }

    public void setCurrentPassword(String currentPassword) {
        this.currentPassword = currentPassword;
    }
}