package com.sis.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Read-only representation of a Grade returned to API clients.
 * Never expose the {@code Grade} entity directly - it carries a JPA-managed
 * Student/Course graph that would either lazy-load explode or leak far more
 * than the client needs.
 */
public class GradeDto {
    private Long id;
    private Long courseId;
    private String courseCode;
    private String courseName;
    private String gradeValue;
    private BigDecimal gpaScore;
    private String remarks;
    private Integer credits;
    private String semester;
    private String status;
    private String submittedBy;
    private String approvedBy;
    private String rejectionReason;
    private Integer attemptNumber;
    private Long originalAttemptId;
    private String attemptType;
    private BigDecimal totalMark;
    private Boolean retakeRequired;
    private String gradingPolicyVersion;
    private List<AssessmentMarkDto> assessmentComponents;

    public GradeDto() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public String getCourseCode() { return courseCode; }
    public void setCourseCode(String courseCode) { this.courseCode = courseCode; }
    public String getCourseName() { return courseName; }
    public void setCourseName(String courseName) { this.courseName = courseName; }
    public String getGradeValue() { return gradeValue; }
    public void setGradeValue(String gradeValue) { this.gradeValue = gradeValue; }
    public BigDecimal getGpaScore() { return gpaScore; }
    public void setGpaScore(BigDecimal gpaScore) { this.gpaScore = gpaScore; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
    public Integer getCredits() { return credits; }
    public void setCredits(Integer credits) { this.credits = credits; }
    public String getSemester() { return semester; }
    public void setSemester(String semester) { this.semester = semester; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(String submittedBy) { this.submittedBy = submittedBy; }
    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    public Integer getAttemptNumber() { return attemptNumber; }
    public void setAttemptNumber(Integer attemptNumber) { this.attemptNumber = attemptNumber; }
    public Long getOriginalAttemptId() { return originalAttemptId; }
    public void setOriginalAttemptId(Long originalAttemptId) { this.originalAttemptId = originalAttemptId; }
    public String getAttemptType() { return attemptType; }
    public void setAttemptType(String attemptType) { this.attemptType = attemptType; }
    public BigDecimal getTotalMark() { return totalMark; }
    public void setTotalMark(BigDecimal totalMark) { this.totalMark = totalMark; }
    public Boolean getRetakeRequired() { return retakeRequired; }
    public void setRetakeRequired(Boolean retakeRequired) { this.retakeRequired = retakeRequired; }
    public String getGradingPolicyVersion() { return gradingPolicyVersion; }
    public void setGradingPolicyVersion(String gradingPolicyVersion) { this.gradingPolicyVersion = gradingPolicyVersion; }
    public List<AssessmentMarkDto> getAssessmentComponents() { return assessmentComponents; }
    public void setAssessmentComponents(List<AssessmentMarkDto> assessmentComponents) { this.assessmentComponents = assessmentComponents; }
}
