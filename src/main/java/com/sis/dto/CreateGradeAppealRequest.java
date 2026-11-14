package com.sis.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

public class CreateGradeAppealRequest {
    @NotNull
    private Long gradeId;

    @NotBlank
    @Size(max = 2000)
    private String reason;

    public Long getGradeId() { return gradeId; }
    public void setGradeId(Long gradeId) { this.gradeId = gradeId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
