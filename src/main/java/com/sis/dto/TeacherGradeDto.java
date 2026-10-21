package com.sis.dto;

import java.time.LocalDateTime;

public class TeacherGradeDto {
    private final Long id;
    private final Long studentId;
    private final String studentName;
    private final String gradeValue;
    private final String remarks;
    private final String status;
    private final String rejectionReason;
    private final LocalDateTime submittedAt;

    public TeacherGradeDto(Long id, Long studentId, String studentName, String gradeValue,
                           String remarks, String status, String rejectionReason, LocalDateTime submittedAt) {
        this.id = id;
        this.studentId = studentId;
        this.studentName = studentName;
        this.gradeValue = gradeValue;
        this.remarks = remarks;
        this.status = status;
        this.rejectionReason = rejectionReason;
        this.submittedAt = submittedAt;
    }

    public Long getId() { return id; }
    public Long getStudentId() { return studentId; }
    public String getStudentName() { return studentName; }
    public String getGradeValue() { return gradeValue; }
    public String getRemarks() { return remarks; }
    public String getStatus() { return status; }
    public String getRejectionReason() { return rejectionReason; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
}