package com.sis.dto;

import java.time.LocalDate;

public class StudentProgramLifecycleDto {
    private final Long id;
    private final Long programId;
    private final String programName;
    private final Long academicYearId;
    private final String academicYearName;
    private final String status;
    private final LocalDate startDate;
    private final LocalDate endDate;

    public StudentProgramLifecycleDto(Long id, Long programId, String programName, Long academicYearId,
                                      String academicYearName, String status, LocalDate startDate,
                                      LocalDate endDate) {
        this.id = id;
        this.programId = programId;
        this.programName = programName;
        this.academicYearId = academicYearId;
        this.academicYearName = academicYearName;
        this.status = status;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public Long getId() { return id; }
    public Long getProgramId() { return programId; }
    public String getProgramName() { return programName; }
    public Long getAcademicYearId() { return academicYearId; }
    public String getAcademicYearName() { return academicYearName; }
    public String getStatus() { return status; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
}
