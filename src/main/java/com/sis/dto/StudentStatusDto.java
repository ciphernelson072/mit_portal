package com.sis.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
/**
 * Combined "at a glance" dashboard payload for a logged-in student:
 * current program and lifecycle status, outstanding balance, next fee deadline,
 * recent grades and aggregate attendance.
 * Replaces the previous handmade {@code Map<String,Object>} that was being
 * assembled with business logic sitting directly inside StudentController.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudentStatusDto {
    private BigDecimal balance;
    private String nextDueDate;
    private String studentProgramName;
    private String studentLifecycleStatus;
    private List<GradeDto> grades;
    private long attendanceCount;
    private long presentAttendanceCount;
}
