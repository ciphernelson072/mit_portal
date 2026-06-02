package com.sis.controller;

import com.sis.entity.Attendance;
import com.sis.service.AttendanceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Attendance Controller - Manages student attendance records
 * Endpoints for recording, viewing, and managing attendance
 */
@RestController
@RequestMapping("/api/attendance")
@PreAuthorize("hasAuthority('ADMIN') or hasAuthority('TEACHER')")
@CrossOrigin(origins = "*")
public class AttendanceController {

    @Autowired
    private AttendanceService attendanceService;

    /**
     * Record attendance for student in course
     * @param studentId student ID
     * @param courseId course ID
     * @param date attendance date
     * @param present presence status
     * @param remarks optional remarks
     * @return created attendance record
     */
    @PostMapping("/record")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('TEACHER')")
    public ResponseEntity<Attendance> recordAttendance(
            @RequestParam Long studentId,
            @RequestParam Long courseId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam boolean present,
            @RequestParam(required = false) String remarks) {
        Attendance attendance = attendanceService.recordAttendance(studentId, courseId, date, present, remarks);
        return ResponseEntity.ok(attendance);
    }

    /**
     * Get all attendance records for a student
     * @param studentId student ID
     * @return list of attendance records
     */
    @GetMapping("/student/{studentId}")
    public ResponseEntity<List<Attendance>> getStudentAttendance(@PathVariable Long studentId) {
        List<Attendance> attendance = attendanceService.getStudentAttendance(studentId);
        return ResponseEntity.ok(attendance);
    }

    /**
     * Get attendance percentage for student in course
     * @param studentId student ID
     * @param courseId course ID
     * @return attendance percentage
     */
    @GetMapping("/percentage")
    public ResponseEntity<Float> getAttendancePercentage(
            @RequestParam Long studentId,
            @RequestParam Long courseId) {
        float percentage = attendanceService.getAttendancePercentage(studentId, courseId);
        return ResponseEntity.ok(percentage);
    }

    /**
     * Get attendance records between dates
     * @param studentId student ID
     * @param startDate start date
     * @param endDate end date
     * @return list of attendance records
     */
    @GetMapping("/range")
    public ResponseEntity<List<Attendance>> getAttendanceByDateRange(
            @RequestParam Long studentId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<Attendance> attendance = attendanceService.getAttendanceByDateRange(studentId, startDate, endDate);
        return ResponseEntity.ok(attendance);
    }

    /**
     * Update attendance record
     * @param attendanceId attendance ID
     * @param present presence status
     * @param remarks remarks
     * @return updated attendance record
     */
    @PutMapping("/{attendanceId}")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('TEACHER')")
    public ResponseEntity<Attendance> updateAttendance(
            @PathVariable Long attendanceId,
            @RequestParam boolean present,
            @RequestParam(required = false) String remarks) {
        Attendance attendance = attendanceService.updateAttendance(attendanceId, present, remarks);
        return ResponseEntity.ok(attendance);
    }

    /**
     * Delete attendance record
     * @param attendanceId attendance ID
     * @return success message
     */
    @DeleteMapping("/{attendanceId}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<String> deleteAttendance(@PathVariable Long attendanceId) {
        attendanceService.deleteAttendance(attendanceId);
        return ResponseEntity.ok("Attendance record deleted");
    }

    /**
     * Get attendance record by ID
     * @param attendanceId attendance ID
     * @return attendance record
     */
    @GetMapping("/{attendanceId}")
    public ResponseEntity<Attendance> getAttendanceById(@PathVariable Long attendanceId) {
        Attendance attendance = attendanceService.getAttendanceById(attendanceId);
        return ResponseEntity.ok(attendance);
    }
}
