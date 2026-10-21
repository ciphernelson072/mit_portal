package com.sis.controller;

import com.sis.dto.AttendanceDto;
import com.sis.service.AttendanceService;
import com.sis.service.SchoolService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Attendance Controller - Manages student attendance records.
 * Endpoints for recording, viewing, and managing attendance.
 * CORS is handled centrally by {@code config.WebConfig} - no per-controller
 * {@code @CrossOrigin(origins = "*")} here anymore.
 */
@RestController
@RequestMapping("/api/attendance")
@PreAuthorize("@permissionSecurity.canManageAttendance()")
public class AttendanceController {

    @Autowired
    private AttendanceService attendanceService;

    @Autowired
    private SchoolService schoolService;

    @GetMapping("/student/{studentId}")
    @PreAuthorize("@permissionSecurity.canManageAttendance() or @studentAccessService.isOwnStudent(#studentId)")
    public ResponseEntity<List<AttendanceDto>> getStudentAttendance(@PathVariable Long studentId) {
        List<AttendanceDto> attendance = attendanceService.getStudentAttendance(studentId).stream()
                .map(schoolService::toAttendanceDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(attendance);
    }

    @GetMapping("/percentage")
    @PreAuthorize("@permissionSecurity.canManageAttendance() or @studentAccessService.isOwnStudent(#studentId)")
    public ResponseEntity<Float> getAttendancePercentage(
            @RequestParam Long studentId,
            @RequestParam Long courseId) {
        float percentage = attendanceService.getAttendancePercentage(studentId, courseId);
        return ResponseEntity.ok(percentage);
    }

    @GetMapping("/range")
    @PreAuthorize("@permissionSecurity.canManageAttendance() or @studentAccessService.isOwnStudent(#studentId)")
    public ResponseEntity<List<AttendanceDto>> getAttendanceByDateRange(
            @RequestParam Long studentId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<AttendanceDto> attendance = attendanceService.getAttendanceByDateRange(studentId, startDate, endDate).stream()
                .map(schoolService::toAttendanceDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(attendance);
    }

}
