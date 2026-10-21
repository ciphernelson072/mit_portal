package com.sis.dto;

import java.time.LocalDateTime;

public class StudentStatusHistoryDto {
    private final Long id;
    private final String status;
    private final String reason;
    private final String changedBy;
    private final LocalDateTime changedAt;
    private final Long studentProgramId;
    private final String programName;
    private final String academicYearName;

    public StudentStatusHistoryDto(Long id, String status, String reason, String changedBy,
                                   LocalDateTime changedAt, Long studentProgramId,
                                   String programName, String academicYearName) {
        this.id = id;
        this.status = status;
        this.reason = reason;
        this.changedBy = changedBy;
        this.changedAt = changedAt;
        this.studentProgramId = studentProgramId;
        this.programName = programName;
        this.academicYearName = academicYearName;
    }

    public Long getId() { return id; }
    public String getStatus() { return status; }
    public String getReason() { return reason; }
    public String getChangedBy() { return changedBy; }
    public LocalDateTime getChangedAt() { return changedAt; }
    public Long getStudentProgramId() { return studentProgramId; }
    public String getProgramName() { return programName; }
    public String getAcademicYearName() { return academicYearName; }
}
