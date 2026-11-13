package com.sis.dto;

import com.sis.entity.ResearchProjectStatus;
import javax.validation.constraints.NotNull;

public class ReviewResearchProjectRequest {
    @NotNull private ResearchProjectStatus status;
    public ResearchProjectStatus getStatus() { return status; }
    public void setStatus(ResearchProjectStatus status) { this.status = status; }
}