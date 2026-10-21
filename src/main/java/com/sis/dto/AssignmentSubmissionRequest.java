package com.sis.dto;

import javax.validation.constraints.Size;

public class AssignmentSubmissionRequest {
    @Size(max = 2000)
    private String content = "";

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
