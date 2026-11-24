package com.sis.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

public class MfaSetupResponse {

    private final String secret;
    private final String otpUri;
    private final String qrCodeDataUri;
    private final List<String> recoveryCodes;

    public MfaSetupResponse(String secret, String otpUri) {
        this(secret, otpUri, null, List.of());
    }

    public MfaSetupResponse(String secret, String otpUri, List<String> recoveryCodes) {
        this(secret, otpUri, null, recoveryCodes);
    }

    public MfaSetupResponse(String secret, String otpUri, String qrCodeDataUri) {
        this(secret, otpUri, qrCodeDataUri, List.of());
    }

    @JsonCreator
    public MfaSetupResponse(
            @JsonProperty("secret") String secret,
            @JsonProperty("otpUri") String otpUri,
            @JsonProperty("qrCodeDataUri") String qrCodeDataUri,
            @JsonProperty("recoveryCodes") List<String> recoveryCodes) {
        this.secret = secret;
        this.otpUri = otpUri;
        this.qrCodeDataUri = qrCodeDataUri;
        this.recoveryCodes = recoveryCodes == null ? new ArrayList<>() : List.copyOf(recoveryCodes);
    }

    public String getSecret() {
        return secret;
    }

    public String getOtpUri() {
        return otpUri;
    }

    public String getQrCodeDataUri() {
        return qrCodeDataUri;
    }

    public List<String> getRecoveryCodes() {
        return recoveryCodes;
    }
}