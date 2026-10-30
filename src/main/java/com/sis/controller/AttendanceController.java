package com.sis.controller;

import com.sis.dto.AttendanceDto;
import com.sis.service.AttendanceService;
import com.sis.service.SchoolService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
public class AttendanceController {

    @Autowired
    private AttendanceService attendanceService;

    @Autowired
    private SchoolService schoolService;

    @GetMapping("/student/{studentId}")
    @PreAuthorize("@studentAccessService.isOwnStudent(#studentId) or @permissionSecurity.canManageAttendance()")
    public ResponseEntity<List<AttendanceDto>> getStudentAttendance(@PathVariable Long studentId) {
        List<AttendanceDto> attendance = attendanceService.getStudentAttendance(studentId).stream()
                .filter(record -> canViewAttendanceCourse(record.getCourse()))
                .map(schoolService::toAttendanceDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(attendance);
    }

    @GetMapping("/percentage")
    @PreAuthorize("@studentAccessService.isOwnStudent(#studentId) or "
            + "@permissionSecurity.canManageAttendance() and "
            + "(!hasAuthority('TEACHER') or hasAuthority('ADMIN') or "
            + "@teacherAccessService.teachesStudentInCourse(#studentId, #courseId))")
    public ResponseEntity<Float> getAttendancePercentage(
            @RequestParam Long studentId,
            @RequestParam Long courseId) {
        float percentage = attendanceService.getAttendancePercentage(studentId, courseId);
        return ResponseEntity.ok(percentage);
    }

    @GetMapping("/range")
    @PreAuthorize("@studentAccessService.isOwnStudent(#studentId) or @permissionSecurity.canManageAttendance()")
    public ResponseEntity<List<AttendanceDto>> getAttendanceByDateRange(
            @RequestParam Long studentId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<AttendanceDto> attendance = attendanceService.getAttendanceByDateRange(studentId, startDate, endDate).stream()
                .filter(record -> canViewAttendanceCourse(record.getCourse()))
                .map(schoolService::toAttendanceDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(attendance);
    }

    private boolean canViewAttendanceCourse(com.sis.entity.Course course) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        boolean teacherWithoutAdminAuthority = authentication.getAuthorities().stream()
                .anyMatch(authority -> "TEACHER".equals(authority.getAuthority()))
                && authentication.getAuthorities().stream()
                .noneMatch(authority -> "ADMIN".equals(authority.getAuthority()));
        return !teacherWithoutAdminAuthority || schoolService.teacherOwnsCourse(
                authentication.getName(), course);
    }

}
