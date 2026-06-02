package com.sis.dto;

import java.math.BigDecimal;

/**
 * Course Grade DTO - Represents grade information for a course
 */
public class CourseGradeDto {

    /**
     * Course ID
     */
    private Long courseId;

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
     * GPA score (0.0-4.0)
     */
    private BigDecimal gpaScore;

    /**
     * Weighted GPA (grade * credits)
     */
    private BigDecimal weightedGpa;

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
