package com.sis.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class GradeAppealTeacherResponse {
    @NotBlank
    @Size(max = 2000)
    private String response;

    public String getResponse() { return response; }
    public void setResponse(String response) { this.response = response; }
}
