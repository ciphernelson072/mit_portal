package com.sis.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public class ReportExportRequest {
    @NotBlank
    private String title;
    private List<String> lines;

    public ReportExportRequest() {
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<String> getLines() {
        return lines;
    }

    public void setLines(List<String> lines) {
        this.lines = lines;
    }
}
