package com.sis.dto;

import java.math.BigDecimal;
import java.util.List;

public record ExamReadinessDto(
        boolean enrolled,
        boolean hasAttendanceRecords,
        long attendanceSessions,
        BigDecimal attendancePercentage,
        boolean belowAttendanceThreshold,
        boolean activeFinancialHold,
        List<String> warnings) {

    public ExamReadinessDto {
        warnings = List.copyOf(warnings);
    }
}
