package com.sis.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CreateServiceRequest {
    @NotBlank
    @Size(max = 40)
    private String type;

    @NotBlank
    @Size(max = 255)
    private String subject;

    @Size(max = 2000)
    private String details;

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
}
