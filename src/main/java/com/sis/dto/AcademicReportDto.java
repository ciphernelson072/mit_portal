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
