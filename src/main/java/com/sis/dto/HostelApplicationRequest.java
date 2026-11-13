package com.sis.dto;

import javax.validation.constraints.Size;

public class HostelApplicationRequest {
    @Size(max = 2000)
    private String details = "";

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }
}
