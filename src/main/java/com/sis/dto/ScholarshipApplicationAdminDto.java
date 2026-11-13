package com.sis.dto;

import java.time.LocalDateTime;

public class ScholarshipApplicationAdminDto {
    private final Long id;
    private final Long scholarshipId;
    private final Long studentId;
    private final String studentName;
    private final String registrationNumber;
    private final String status;
    private final LocalDateTime appliedAt;

    public ScholarshipApplicationAdminDto(Long id, Long scholarshipId, Long studentId, String studentName,
                                          String registrationNumber, String status, LocalDateTime appliedAt) {
        this.id = id;
        this.scholarshipId = scholarshipId;
        this.studentId = studentId;
        this.studentName = studentName;
        this.registrationNumber = registrationNumber;
        this.status = status;
        this.appliedAt = appliedAt;
    }

    public Long getId() { return id; }
    public Long getScholarshipId() { return scholarshipId; }
    public Long getStudentId() { return studentId; }
    public String getStudentName() { return studentName; }
    public String getRegistrationNumber() { return registrationNumber; }
    public String getStatus() { return status; }
    public LocalDateTime getAppliedAt() { return appliedAt; }
}
