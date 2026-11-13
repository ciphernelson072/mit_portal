package com.sis.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.time.LocalDate;

public class CreateAcademicTermRequest {
    @NotBlank
    @Size(max = 30)
    private String code;

    @NotBlank
    @Size(max = 100)
    private String name;

    @NotNull
    private Long academicYearId;

    @NotNull
    private LocalDate startDate;

    @NotNull
    private LocalDate endDate;

    @NotNull
    private Boolean registrationOpen;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getAcademicYearId() { return academicYearId; }
    public void setAcademicYearId(Long academicYearId) { this.academicYearId = academicYearId; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public Boolean getRegistrationOpen() { return registrationOpen; }
    public void setRegistrationOpen(Boolean registrationOpen) { this.registrationOpen = registrationOpen; }
}
