package com.sis.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "grades")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Grade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "row_version", nullable = false)
    private Long rowVersion;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id")
    private Student student;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private Course course;

    @Column(nullable = false)
    private String gradeValue;

    @Column(name = "grading_policy_version", nullable = false, length = 32)
    private String gradingPolicyVersion = "UG_5_POINT_V1";

    @Column(name = "retake_required", nullable = false)
    private Boolean retakeRequired = false;

    @Column(name = "attempt_number", nullable = false)
    private Integer attemptNumber = 1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_attempt_id")
    private Grade originalAttempt;

    @Column(name = "total_mark", precision = 6, scale = 2)
    private BigDecimal totalMark;

    @OneToMany(mappedBy = "grade", fetch = FetchType.LAZY)
    private java.util.List<GradeAssessmentMark> assessmentMarks = new java.util.ArrayList<>();

    // Numeric grade point for CGPA calculations (0.0 - 5.0)
    @Column(nullable = false, precision = 3, scale = 2)
    private BigDecimal gpaScore = BigDecimal.ZERO;

    @Column(length = 1024)
    private String remarks;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GradeStatus status = GradeStatus.PENDING;

    @Column
    private String submittedBy;

    @Column
    private String approvedBy;

    @Column
    private String rejectedBy;

    @Column
    private String rejectionReason;

    @Column
    private LocalDateTime submittedAt;

    @Column
    private LocalDateTime approvedAt;

    @Column
    private LocalDateTime rejectedAt;

    @Column(name = "assessment_review_fingerprint", columnDefinition = "CHAR(64)")
    private String assessmentReviewFingerprint;

    @Column(name = "assessment_reviewed_by")
    private String assessmentReviewedBy;

    @Column(name = "assessment_reviewed_at")
    private LocalDateTime assessmentReviewedAt;

    public Grade() {
    }

    public Grade(Student student, Course course, String gradeValue, String remarks) {
        this.student = student;
        this.course = course;
        this.gradeValue = gradeValue;
        this.remarks = remarks;
        this.status = GradeStatus.PENDING;
        this.submittedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Student getStudent() {
        return student;
    }

    public void setStudent(Student student) {
        this.student = student;
    }

    public Course getCourse() {
        return course;
    }

    public void setCourse(Course course) {
        this.course = course;
    }

    public String getGradeValue() {
        return gradeValue;
    }

    public void setGradeValue(String gradeValue) {
        this.gradeValue = gradeValue;
    }

    public String getGradingPolicyVersion() {
        return gradingPolicyVersion;
    }

    public void setGradingPolicyVersion(String gradingPolicyVersion) {
        this.gradingPolicyVersion = gradingPolicyVersion;
    }

    public Boolean getRetakeRequired() {
        return retakeRequired;
    }

    public void setRetakeRequired(Boolean retakeRequired) {
        this.retakeRequired = retakeRequired;
    }

    public Integer getAttemptNumber() {
        return attemptNumber;
    }

    public void setAttemptNumber(Integer attemptNumber) {
        this.attemptNumber = attemptNumber;
    }

    public Grade getOriginalAttempt() {
        return originalAttempt;
    }

    public void setOriginalAttempt(Grade originalAttempt) {
        this.originalAttempt = originalAttempt;
    }

    public BigDecimal getTotalMark() {
        return totalMark;
    }

    public void setTotalMark(BigDecimal totalMark) {
        this.totalMark = totalMark;
    }

    public java.util.List<GradeAssessmentMark> getAssessmentMarks() {
        return assessmentMarks;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public GradeStatus getStatus() {
        return status;
    }

    public void setStatus(GradeStatus status) {
        this.status = status;
    }

    public String getSubmittedBy() {
        return submittedBy;
    }

    public void setSubmittedBy(String submittedBy) {
        this.submittedBy = submittedBy;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(String approvedBy) {
        this.approvedBy = approvedBy;
    }

    public String getRejectedBy() {
        return rejectedBy;
    }

    public void setRejectedBy(String rejectedBy) {
        this.rejectedBy = rejectedBy;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(LocalDateTime approvedAt) {
        this.approvedAt = approvedAt;
    }

    public LocalDateTime getRejectedAt() {
        return rejectedAt;
    }

    public void setRejectedAt(LocalDateTime rejectedAt) {
        this.rejectedAt = rejectedAt;
    }

    public String getAssessmentReviewFingerprint() {
        return assessmentReviewFingerprint;
    }

    public void setAssessmentReviewFingerprint(String assessmentReviewFingerprint) {
        this.assessmentReviewFingerprint = assessmentReviewFingerprint;
    }

    public String getAssessmentReviewedBy() {
        return assessmentReviewedBy;
    }

    public void setAssessmentReviewedBy(String assessmentReviewedBy) {
        this.assessmentReviewedBy = assessmentReviewedBy;
    }

    public LocalDateTime getAssessmentReviewedAt() {
        return assessmentReviewedAt;
    }

    public void setAssessmentReviewedAt(LocalDateTime assessmentReviewedAt) {
        this.assessmentReviewedAt = assessmentReviewedAt;
    }

    /**
     * Get numeric GPA score for this grade
     * @return GPA score as BigDecimal (0.0 - 4.0)
     */
    public BigDecimal getGpaScore() {
        return gpaScore;
    }

    /**
     * Set numeric GPA score for this grade
     * @param gpaScore GPA value for CGPA calculations
     */
    public void setGpaScore(BigDecimal gpaScore) {
        this.gpaScore = gpaScore;
    }
}
