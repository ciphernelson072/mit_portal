package com.sis.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Academic Report DTO - Data transfer object for academic performance reports
 * Contains comprehensive academic data for a student
 */
public class AcademicReportDto {

    /**
     * Student ID
     */
    private Long studentId;

    /**
     * Student full name
     */
    private String studentName;

    /**
     * Student class/grade
     */
    private String className;

    /**
     * Current CGPA
     */
    private BigDecimal cgpa;
    private String awardClassification;
    private Integer creditsEarned;
    private String latestSemester;
    private BigDecimal semesterGpa;
    private String academicStandingStatus;

    /**
     * Total courses enrolled
     */
    private Integer totalCourses;

    /**
     * Average grade
     */
    private String averageGrade;

    /**
     * Attendance percentage
     */
    private Float attendancePercentage;

    /**
     * List of courses with grades
     */
    private List<CourseGradeDto> courseGrades;

    /**
     * Performance summary
     */
    private String performanceSummary;

    /**
     * Report generation date
     */
    private String generatedDate;

    /**
     * Whether this official academic report has been approved.
     */
    private Boolean approved;

    /**
     * Name of the approver who released the official report.
     */
    private String approvedBy;

    /**
     * Approval timestamp used in the formal report.
     */
    private String approvedAt;

    private Boolean published;

    private String publishedBy;

    private String publishedAt;

    private String publicationStatus;

    private String resultsVersion;

    // Constructors
    public AcademicReportDto() {}

    public AcademicReportDto(Long studentId, String studentName, String className, BigDecimal cgpa) {
        this.studentId = studentId;
        this.studentName = studentName;
        this.className = className;
        this.cgpa = cgpa;
    }

    // Getters and Setters
    public Long getStudentId() {
        return studentId;
    }

    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }

    public String getStudentName() {
        return studentName;
    }

    public void setStudentName(String studentName) {
        this.studentName = studentName;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public BigDecimal getCgpa() {
        return cgpa;
    }

    public void setCgpa(BigDecimal cgpa) {
        this.cgpa = cgpa;
    }

    public String getAwardClassification() { return awardClassification; }
    public void setAwardClassification(String awardClassification) { this.awardClassification = awardClassification; }
    public Integer getCreditsEarned() { return creditsEarned; }
    public void setCreditsEarned(Integer creditsEarned) { this.creditsEarned = creditsEarned; }
    public String getLatestSemester() { return latestSemester; }
    public void setLatestSemester(String latestSemester) { this.latestSemester = latestSemester; }
    public BigDecimal getSemesterGpa() { return semesterGpa; }
    public void setSemesterGpa(BigDecimal semesterGpa) { this.semesterGpa = semesterGpa; }
    public String getAcademicStandingStatus() { return academicStandingStatus; }
    public void setAcademicStandingStatus(String academicStandingStatus) { this.academicStandingStatus = academicStandingStatus; }

    public Integer getTotalCourses() {
        return totalCourses;
    }

    public void setTotalCourses(Integer totalCourses) {
        this.totalCourses = totalCourses;
    }

    public String getAverageGrade() {
        return averageGrade;
    }

    public void setAverageGrade(String averageGrade) {
        this.averageGrade = averageGrade;
    }

    public Float getAttendancePercentage() {
        return attendancePercentage;
    }

    public void setAttendancePercentage(Float attendancePercentage) {
        this.attendancePercentage = attendancePercentage;
    }

    public List<CourseGradeDto> getCourseGrades() {
        return courseGrades;
    }

    public void setCourseGrades(List<CourseGradeDto> courseGrades) {
        this.courseGrades = courseGrades;
    }

    public String getPerformanceSummary() {
        return performanceSummary;
    }

    public void setPerformanceSummary(String performanceSummary) {
        this.performanceSummary = performanceSummary;
    }

    public String getGeneratedDate() {
        return generatedDate;
    }

    public void setGeneratedDate(String generatedDate) {
        this.generatedDate = generatedDate;
    }

    public Boolean getApproved() {
        return approved;
    }

    public void setApproved(Boolean approved) {
        this.approved = approved;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(String approvedBy) {
        this.approvedBy = approvedBy;
    }

    public String getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(String approvedAt) {
        this.approvedAt = approvedAt;
    }

    public Boolean getPublished() {
        return published;
    }

    public void setPublished(Boolean published) {
        this.published = published;
    }

    public String getPublishedBy() {
        return publishedBy;
    }

    public void setPublishedBy(String publishedBy) {
        this.publishedBy = publishedBy;
    }

    public String getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(String publishedAt) {
        this.publishedAt = publishedAt;
    }

    public String getPublicationStatus() {
        return publicationStatus;
    }

    public void setPublicationStatus(String publicationStatus) {
        this.publicationStatus = publicationStatus;
    }

    public String getResultsVersion() {
        return resultsVersion;
    }

    public void setResultsVersion(String resultsVersion) {
        this.resultsVersion = resultsVersion;
    }

    @Override
    public String toString() {
        return "AcademicReportDto{" +
                "studentId=" + studentId +
                ", studentName='" + studentName + '\'' +
                ", className='" + className + '\'' +
                ", cgpa=" + cgpa +
                ", totalCourses=" + totalCourses +
                ", averageGrade='" + averageGrade + '\'' +
                ", attendancePercentage=" + attendancePercentage +
                '}';
    }
}
