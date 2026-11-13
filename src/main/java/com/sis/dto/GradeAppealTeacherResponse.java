package com.sis.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class GradeAppealTeacherResponse {
    @NotBlank
    @Size(max = 2000)
    private String response;

    public String getResponse() { return response; }
    public void setResponse(String response) { this.response = response; }
}
