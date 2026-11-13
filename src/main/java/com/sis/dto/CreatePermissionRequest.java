package com.sis.dto;

import javax.validation.constraints.NotBlank;

public class CreatePermissionRequest {
    @NotBlank private String code;
    @NotBlank private String description;
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}