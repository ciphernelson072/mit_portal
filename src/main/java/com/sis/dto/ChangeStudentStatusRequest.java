package com.sis.dto;

import com.sis.entity.StudentLifecycleStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class ChangeStudentStatusRequest {
    @NotNull
    @Positive
    private Long academicYearId;

    @NotNull
    @Positive
    private Long programId;

    @NotNull
    private StudentLifecycleStatus status;

    @NotBlank
    @Size(max = 2000)
    private String reason;

    public Long getAcademicYearId() { return academicYearId; }
    public void setAcademicYearId(Long academicYearId) { this.academicYearId = academicYearId; }
    public Long getProgramId() { return programId; }
    public void setProgramId(Long programId) { this.programId = programId; }
    public StudentLifecycleStatus getStatus() { return status; }
    public void setStatus(StudentLifecycleStatus status) { this.status = status; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}