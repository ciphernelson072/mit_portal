package com.sis.dto;

import com.sis.entity.ProgramAwardType;

import jakarta.validation.constraints.NotNull;

public class UpdateProgramAwardTypeRequest {
    @NotNull
    private ProgramAwardType awardType;

    public ProgramAwardType getAwardType() {
        return awardType;
    }

    public void setAwardType(ProgramAwardType awardType) {
        this.awardType = awardType;
    }
}
