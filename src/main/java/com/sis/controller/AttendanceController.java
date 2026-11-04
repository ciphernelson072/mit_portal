package com.sis.controller;

import com.sis.dto.AttendanceDto;
import com.sis.dto.AttendanceDisputeDto;
import com.sis.dto.CreateAttendanceDisputeRequest;
import com.sis.dto.ReviewAttendanceDisputeRequest;
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
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import jakarta.validation.Valid;

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

    @PostMapping("/disputes")
    @PreAuthorize("hasAuthority('STUDENT')")
    public ResponseEntity<AttendanceDisputeDto> createDispute(
            @Valid @RequestBody CreateAttendanceDisputeRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                .body(attendanceService.createDispute(username, request));
    }

    @GetMapping("/disputes")
    @PreAuthorize("hasAuthority('STUDENT')")
    public ResponseEntity<List<AttendanceDisputeDto>> studentDisputes() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(attendanceService.listStudentDisputes(username));
    }

    @GetMapping("/disputes/admin")
    @PreAuthorize("@permissionSecurity.canManageAttendance()")
    public ResponseEntity<List<AttendanceDisputeDto>> allDisputes() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean teacherWithoutAdminAuthority = authentication != null
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> "TEACHER".equals(authority.getAuthority()))
                && authentication.getAuthorities().stream()
                .noneMatch(authority -> "ADMIN".equals(authority.getAuthority()));
        if (teacherWithoutAdminAuthority) {
            String username = authentication.getName();
            var teacher = schoolService.getTeacherByUsername(username)
                    .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException(
                            "Teacher profile not found."));
            List<AttendanceDisputeDto> disputes = schoolService.getCoursesForTeacher(teacher.getId()).stream()
                    .filter(course -> course.getId() != null)
                    .map(course -> attendanceService.listCourseDisputes(username, course.getId()))
                    .flatMap(List::stream)
                    .sorted(Comparator.comparing(AttendanceDisputeDto::getCreatedAt,
                            Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList();
            return ResponseEntity.ok(disputes);
        }
        return ResponseEntity.ok(attendanceService.listAllDisputes());
    }

    @PatchMapping("/disputes/{disputeId}/review")
    @PreAuthorize("@permissionSecurity.canManageAttendance()")
    public ResponseEntity<AttendanceDisputeDto> reviewDispute(
            @PathVariable Long disputeId, @Valid @RequestBody ReviewAttendanceDisputeRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(attendanceService.reviewDispute(disputeId, request, username));
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
