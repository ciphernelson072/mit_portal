package com.sis.dto;

import java.math.BigDecimal;

public class AdminGradeDto {
    private Long id;
    private Long studentId;
    private String studentName;
    private String studentUsername;
    private Long courseId;
    private String courseName;
    private String gradeValue;
    private BigDecimal gpaScore;
    private String remarks;
    private String status;
    private String submittedBy;
    private String approvedBy;
    private String rejectedBy;
    private String rejectionReason;

    public AdminGradeDto(Long id, Long studentId, String studentName, String studentUsername,
                         Long courseId, String courseName, String gradeValue,
                         BigDecimal gpaScore, String remarks, String status,
                         String submittedBy, String approvedBy, String rejectedBy,
                         String rejectionReason) {
        this.id = id;
        this.studentId = studentId;
        this.studentName = studentName;
        this.studentUsername = studentUsername;
        this.courseId = courseId;
        this.courseName = courseName;
        this.gradeValue = gradeValue;
        this.gpaScore = gpaScore;
        this.remarks = remarks;
        this.status = status;
        this.submittedBy = submittedBy;
        this.approvedBy = approvedBy;
        this.rejectedBy = rejectedBy;
        this.rejectionReason = rejectionReason;
    }

    public Long getId() { return id; }
    public Long getStudentId() { return studentId; }
    public String getStudentName() { return studentName; }
    public String getStudentUsername() { return studentUsername; }
    public Long getCourseId() { return courseId; }
    public String getCourseName() { return courseName; }
    public String getGradeValue() { return gradeValue; }
    public BigDecimal getGpaScore() { return gpaScore; }
    public String getRemarks() { return remarks; }
    public String getStatus() { return status; }
    public String getSubmittedBy() { return submittedBy; }
    public String getApprovedBy() { return approvedBy; }
    public String getRejectedBy() { return rejectedBy; }
    public String getRejectionReason() { return rejectionReason; }
}