package com.sis.dto;

/**
 * Minimal student-facing view of a grade before official results are published.
 */
public class StudentGradeStatusDto {
    private String courseCode;
    private String courseName;
    private String status;

    public StudentGradeStatusDto() {}

    public StudentGradeStatusDto(String courseCode, String courseName, String status) {
        this.courseCode = courseCode;
        this.courseName = courseName;
        this.status = status;
    }

    public String getCourseCode() { return courseCode; }
    public void setCourseCode(String courseCode) { this.courseCode = courseCode; }
    public String getCourseName() { return courseName; }
    public void setCourseName(String courseName) { this.courseName = courseName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
