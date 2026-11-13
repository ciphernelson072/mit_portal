package com.sis.dto;

public class MfaSetupResponse {

    private final String secret;
    private final String otpUri;

    public MfaSetupResponse(String secret, String otpUri) {
        this.secret = secret;
        this.otpUri = otpUri;
    }

    public String getSecret() {
        return secret;
    }

    public String getOtpUri() {
        return otpUri;
    }
}