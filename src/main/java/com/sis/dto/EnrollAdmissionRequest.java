package com.sis.dto;

import javax.validation.constraints.NotNull;

public class EnrollAdmissionRequest {
    @NotNull
    private Long studentId;

    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }
}