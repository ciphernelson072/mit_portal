package com.sis.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class SaveSystemSettingRequest {
    @NotNull
    @Size(max = 4000)
    private String value = "";

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
