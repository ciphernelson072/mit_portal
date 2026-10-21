package com.sis.dto;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

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
