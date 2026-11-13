package com.sis.dto;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class AdmissionStatusRequest {
    @NotBlank
    @Size(max = 30)
    private String applicationNumber;

    @NotBlank
    @Email
    @Size(max = 254)
    private String email;

    public String getApplicationNumber() {
        return applicationNumber;
    }

    public void setApplicationNumber(String applicationNumber) {
        this.applicationNumber = applicationNumber;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
