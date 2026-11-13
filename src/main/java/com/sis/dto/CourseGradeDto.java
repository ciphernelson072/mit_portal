package com.sis.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Course Grade DTO - Represents grade information for a course
 */
public class CourseGradeDto {

    /**
     * Course ID
     */
    private Long courseId;
    private String courseCode;

    /**
     * Course name
     */
    private String courseName;

    /**
     * Course semester
     */
    private String semester;

    /**
     * Course credits
     */
    private Integer credits;

    /**
     * Grade letter (A, B, C, etc.)
     */
    private String grade;

    /**
     * Grade point (0.0-5.0)
     */
    private BigDecimal gpaScore;

    /**
     * Weighted GPA (grade * credits)
     */
    private BigDecimal weightedGpa;
    private BigDecimal totalMark;
    private Boolean retakeRequired;
    private Boolean coreCourse;
    private String gradingPolicyVersion;
    private String status;
    private Integer attemptNumber;
    private Long originalAttemptId;
    private String attemptType;
    private List<AssessmentMarkDto> assessmentComponents;

    /**
     * Teacher name
     */
    private String teacherName;

    // Constructors
    public CourseGradeDto() {}

    public CourseGradeDto(String courseName, String grade, BigDecimal gpaScore) {
        this.courseName = courseName;
        this.grade = grade;
        this.gpaScore = gpaScore;
    }

    // Getters and Setters
    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }

    public String getCourseName() {
        return courseName;
    }

    public void setCourseName(String courseName) {
        this.courseName = courseName;
    }

    public String getCourseCode() {
        return courseCode;
    }

    public void setCourseCode(String courseCode) {
        this.courseCode = courseCode;
    }

    public String getSemester() {
        return semester;
    }

    public void setSemester(String semester) {
        this.semester = semester;
    }

    public Integer getCredits() {
        return credits;
    }

    public void setCredits(Integer credits) {
        this.credits = credits;
    }

    public String getGrade() {
        return grade;
    }

    public void setGrade(String grade) {
        this.grade = grade;
    }

    public BigDecimal getGpaScore() {
        return gpaScore;
    }

    public void setGpaScore(BigDecimal gpaScore) {
        this.gpaScore = gpaScore;
    }

    public BigDecimal getWeightedGpa() {
        return weightedGpa;
    }

    public void setWeightedGpa(BigDecimal weightedGpa) {
        this.weightedGpa = weightedGpa;
    }

    public BigDecimal getTotalMark() {
        return totalMark;
    }

    public void setTotalMark(BigDecimal totalMark) {
        this.totalMark = totalMark;
    }

    public Boolean getRetakeRequired() { return retakeRequired; }
    public void setRetakeRequired(Boolean retakeRequired) { this.retakeRequired = retakeRequired; }
    public Boolean getCoreCourse() { return coreCourse; }
    public void setCoreCourse(Boolean coreCourse) { this.coreCourse = coreCourse; }
    public String getGradingPolicyVersion() { return gradingPolicyVersion; }
    public void setGradingPolicyVersion(String gradingPolicyVersion) { this.gradingPolicyVersion = gradingPolicyVersion; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getAttemptNumber() { return attemptNumber; }
    public void setAttemptNumber(Integer attemptNumber) { this.attemptNumber = attemptNumber; }
    public Long getOriginalAttemptId() { return originalAttemptId; }
    public void setOriginalAttemptId(Long originalAttemptId) { this.originalAttemptId = originalAttemptId; }
    public String getAttemptType() { return attemptType; }
    public void setAttemptType(String attemptType) { this.attemptType = attemptType; }
    public List<AssessmentMarkDto> getAssessmentComponents() { return assessmentComponents; }
    public void setAssessmentComponents(List<AssessmentMarkDto> assessmentComponents) { this.assessmentComponents = assessmentComponents; }

    public String getTeacherName() {
        return teacherName;
    }

    public void setTeacherName(String teacherName) {
        this.teacherName = teacherName;
    }

    @Override
    public String toString() {
        return "CourseGradeDto{" +
                "courseName='" + courseName + '\'' +
                ", semester='" + semester + '\'' +
                ", grade='" + grade + '\'' +
                ", gpaScore=" + gpaScore +
                '}';
    }
}
