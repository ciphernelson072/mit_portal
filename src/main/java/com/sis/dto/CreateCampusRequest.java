package com.sis.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class CreateCampusRequest {
    @NotBlank
    @Size(max = 30)
    private String code;

    @NotBlank
    @Size(max = 150)
    private String name;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
