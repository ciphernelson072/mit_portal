package com.sis.dto;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

public class AttendanceApprovalRequest {
    @NotNull
    private Long courseId;

    @NotNull
    private LocalDate date;

    @NotEmpty
    @Valid
    private List<StudentAttendanceMark> records;

    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public List<StudentAttendanceMark> getRecords() { return records; }
    public void setRecords(List<StudentAttendanceMark> records) { this.records = records; }

    public static class StudentAttendanceMark {
        @NotNull
        private Long studentId;
        @NotNull
        private Boolean present;
        private boolean late;
        private String remarks;

        public Long getStudentId() { return studentId; }
        public void setStudentId(Long studentId) { this.studentId = studentId; }
        public Boolean getPresent() { return present; }
        public void setPresent(Boolean present) { this.present = present; }
        public boolean isLate() { return late; }
        public void setLate(boolean late) { this.late = late; }
        public String getRemarks() { return remarks; }
        public void setRemarks(String remarks) { this.remarks = remarks; }
    }
}