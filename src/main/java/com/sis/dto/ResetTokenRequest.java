package com.sis.dto;

import javax.validation.constraints.NotBlank;

public class ResetTokenRequest {

    @NotBlank(message = "Reset token is required")
    private String token;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
