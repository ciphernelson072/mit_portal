package com.sis.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Read-only representation of an Attendance record returned to API clients.
 */
public class AttendanceDto {
    private Long id;
    private Long studentId;
    private String studentName;
    private Long courseId;
    private String courseName;
    private LocalDate date;
    private boolean present;
    private String remarks;
    private LocalTime classStartTime;
    private LocalTime classEndTime;
    private boolean late;

    public AttendanceDto() {}

    public AttendanceDto(Long id, Long studentId, String studentName, Long courseId, String courseName,
                         LocalDate date, boolean present, String remarks, LocalTime classStartTime,
                         LocalTime classEndTime, boolean late) {
        this.id = id;
        this.studentId = studentId;
        this.studentName = studentName;
        this.courseId = courseId;
        this.courseName = courseName;
        this.date = date;
        this.present = present;
        this.remarks = remarks;
        this.classStartTime = classStartTime;
        this.classEndTime = classEndTime;
        this.late = late;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }
    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public String getCourseName() { return courseName; }
    public void setCourseName(String courseName) { this.courseName = courseName; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public boolean isPresent() { return present; }
    public void setPresent(boolean present) { this.present = present; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
    public LocalTime getClassStartTime() { return classStartTime; }
    public void setClassStartTime(LocalTime classStartTime) { this.classStartTime = classStartTime; }
    public LocalTime getClassEndTime() { return classEndTime; }
    public void setClassEndTime(LocalTime classEndTime) { this.classEndTime = classEndTime; }
    public boolean isLate() { return late; }
    public void setLate(boolean late) { this.late = late; }
}
