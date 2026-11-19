package com.sis.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class PublishAcademicReportRequest {

    @NotBlank
    @Size(min = 64, max = 64)
    private String resultsVersion;

    public String getResultsVersion() {
        return resultsVersion;
    }

    public void setResultsVersion(String resultsVersion) {
        this.resultsVersion = resultsVersion;
    }
}
