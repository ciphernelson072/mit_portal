package com.sis.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

public class GradeAppealDecisionRequest {
    @NotBlank
    @Pattern(regexp = "APPROVED|REJECTED")
    private String decision;

    @NotBlank
    @Size(max = 2000)
    private String note;

    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
