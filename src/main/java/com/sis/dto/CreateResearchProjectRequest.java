package com.sis.dto;

import javax.validation.constraints.NotBlank;

public class CreateResearchProjectRequest {
    @NotBlank private String title;
    private String abstractText;
    private Long supervisorUserId;
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getAbstractText() { return abstractText; }
    public void setAbstractText(String abstractText) { this.abstractText = abstractText; }
    public Long getSupervisorUserId() { return supervisorUserId; }
    public void setSupervisorUserId(Long supervisorUserId) { this.supervisorUserId = supervisorUserId; }
}