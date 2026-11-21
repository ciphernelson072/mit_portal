package com.sis.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

public class MfaSetupResponse {

    private final String secret;
    private final String otpUri;
    private final List<String> recoveryCodes;

    public MfaSetupResponse(String secret, String otpUri) {
        this(secret, otpUri, List.of());
    }

    @JsonCreator
    public MfaSetupResponse(
            @JsonProperty("secret") String secret,
            @JsonProperty("otpUri") String otpUri,
            @JsonProperty("recoveryCodes") List<String> recoveryCodes) {
        this.secret = secret;
        this.otpUri = otpUri;
        this.recoveryCodes = recoveryCodes == null ? new ArrayList<>() : List.copyOf(recoveryCodes);
    }

    public String getSecret() {
        return secret;
    }

    public String getOtpUri() {
        return otpUri;
    }

    public List<String> getRecoveryCodes() {
        return recoveryCodes;
    }
}