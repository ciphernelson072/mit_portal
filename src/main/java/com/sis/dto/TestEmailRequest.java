package com.sis.dto;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;

public class TestEmailRequest {
    @Email
    @NotBlank
    private String recipient;

    public String getRecipient() { return recipient; }
    public void setRecipient(String recipient) { this.recipient = recipient; }
}