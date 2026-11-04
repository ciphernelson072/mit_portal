package com.sis.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class CreateStudentProgramTransferRequest {
    @NotNull
    @Positive
    private Long sourceProgramId;

    @NotNull
    @Positive
    private Long sourceAcademicYearId;

    @NotNull
    @Positive
    private Long destinationProgramId;

    @NotNull
    @Positive
    private Long destinationAcademicYearId;

    @NotBlank
    @Size(max = 1800)
    private String reason;

    public Long getSourceProgramId() { return sourceProgramId; }
    public void setSourceProgramId(Long sourceProgramId) { this.sourceProgramId = sourceProgramId; }
    public Long getSourceAcademicYearId() { return sourceAcademicYearId; }
    public void setSourceAcademicYearId(Long sourceAcademicYearId) { this.sourceAcademicYearId = sourceAcademicYearId; }
    public Long getDestinationProgramId() { return destinationProgramId; }
    public void setDestinationProgramId(Long destinationProgramId) { this.destinationProgramId = destinationProgramId; }
    public Long getDestinationAcademicYearId() { return destinationAcademicYearId; }
    public void setDestinationAcademicYearId(Long destinationAcademicYearId) { this.destinationAcademicYearId = destinationAcademicYearId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
