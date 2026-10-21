package com.sis.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Read-only representation of an Attendance record returned to API clients.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
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
}
