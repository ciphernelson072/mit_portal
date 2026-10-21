package com.sis.dto;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;

public class CreateExamPermitRequest {
    @NotNull
    @Positive
    private Long examId;

    public Long getExamId() {
        return examId;
    }

    public void setExamId(Long examId) {
        this.examId = examId;
    }
}
