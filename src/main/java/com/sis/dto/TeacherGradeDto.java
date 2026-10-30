package com.sis.dto;

import java.math.BigDecimal;
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
    private final Integer attemptNumber;
    private final Long originalAttemptId;
    private final String attemptType;
    private final BigDecimal totalMark;
    private final BigDecimal gpaScore;
    private final Boolean retakeRequired;

    public TeacherGradeDto(Long id, Long studentId, String studentName, String gradeValue,
                           String remarks, String status, String rejectionReason, LocalDateTime submittedAt,
                           Integer attemptNumber, Long originalAttemptId, String attemptType,
                           BigDecimal totalMark, BigDecimal gpaScore, Boolean retakeRequired) {
        this.id = id;
        this.studentId = studentId;
        this.studentName = studentName;
        this.gradeValue = gradeValue;
        this.remarks = remarks;
        this.status = status;
        this.rejectionReason = rejectionReason;
        this.submittedAt = submittedAt;
        this.attemptNumber = attemptNumber;
        this.originalAttemptId = originalAttemptId;
        this.attemptType = attemptType;
        this.totalMark = totalMark;
        this.gpaScore = gpaScore;
        this.retakeRequired = retakeRequired;
    }

    public Long getId() { return id; }
    public Long getStudentId() { return studentId; }
    public String getStudentName() { return studentName; }
    public String getGradeValue() { return gradeValue; }
    public String getRemarks() { return remarks; }
    public String getStatus() { return status; }
    public String getRejectionReason() { return rejectionReason; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public Integer getAttemptNumber() { return attemptNumber; }
    public Long getOriginalAttemptId() { return originalAttemptId; }
    public String getAttemptType() { return attemptType; }
    public BigDecimal getTotalMark() { return totalMark; }
    public BigDecimal getGpaScore() { return gpaScore; }
    public Boolean getRetakeRequired() { return retakeRequired; }
}