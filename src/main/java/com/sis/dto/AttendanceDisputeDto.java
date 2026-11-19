package com.sis.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class AttendanceDisputeDto {
    private Long id;
    private Long attendanceId;
    private Long studentId;
    private String studentName;
    private Long courseId;
    private String courseName;
    private LocalDate attendanceDate;
    private boolean recordedPresent;
    private boolean requestedPresent;
    private String reason;
    private String status;
    private String resolution;
    private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAttendanceId() { return attendanceId; }
    public void setAttendanceId(Long attendanceId) { this.attendanceId = attendanceId; }
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }
    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public String getCourseName() { return courseName; }
    public void setCourseName(String courseName) { this.courseName = courseName; }
    public LocalDate getAttendanceDate() { return attendanceDate; }
    public void setAttendanceDate(LocalDate attendanceDate) { this.attendanceDate = attendanceDate; }
    public boolean isRecordedPresent() { return recordedPresent; }
    public void setRecordedPresent(boolean recordedPresent) { this.recordedPresent = recordedPresent; }
    public boolean isRequestedPresent() { return requestedPresent; }
    public void setRequestedPresent(boolean requestedPresent) { this.requestedPresent = requestedPresent; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
}
