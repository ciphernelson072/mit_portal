package com.sis.controller;

import com.sis.dto.AttendanceDto;
import com.sis.dto.UpdateStudentRequest;
import com.sis.dto.CourseDto;
import com.sis.dto.CourseUnitDto;
import com.sis.dto.GradeDto;
import com.sis.dto.StudentProfileDto;
import com.sis.dto.StudentStatusDto;
import com.sis.service.AttendanceService;
import com.sis.service.SchoolService;
import com.sis.service.AcademicReportService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.List;

/**
 * Student self-service endpoints. Everything here is scoped to the currently
 * authenticated student (resolved from the security context username) - a student
 * can never pass another student's ID to see someone else's data.
 */
@RestController
@RequestMapping("/api/student")
@PreAuthorize("hasAuthority('STUDENT')")
public class StudentController {

    private final SchoolService schoolService;
    private final AttendanceService attendanceService;
    private final AcademicReportService academicReportService;

    public StudentController(SchoolService schoolService, AttendanceService attendanceService,
                             AcademicReportService academicReportService) {
        this.schoolService = schoolService;
        this.attendanceService = attendanceService;
        this.academicReportService = academicReportService;
    }

    @GetMapping("/profile")
    public ResponseEntity<StudentProfileDto> profile() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        StudentProfileDto profile = schoolService.getStudentProfileByUsername(username);
        maskUnpublishedCgpa(profile);
        return profile != null ? ResponseEntity.ok(profile) : ResponseEntity.notFound().build();
    }

    @PutMapping("/profile")
    public ResponseEntity<StudentProfileDto> updateProfile(@Valid @RequestBody UpdateStudentRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return schoolService.updateStudentProfileByUsername(username, request)
                .map(this::maskUnpublishedCgpa)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    private StudentProfileDto maskUnpublishedCgpa(StudentProfileDto profile) {
        if (profile != null && !academicReportService.isOfficiallyPublished(profile.getId())) {
            profile.setCgpa(null);
        }
        return profile;
    }

    @GetMapping("/grades")
    public ResponseEntity<List<GradeDto>> grades() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return schoolService.getStudentByUsername(username)
                .map(student -> ResponseEntity.ok(academicReportService.isOfficiallyPublished(student.getId())
                        ? schoolService.getGradeDtosForStudent(student.getId()) : List.<GradeDto>of()))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/courses")
    public ResponseEntity<List<CourseDto>> courses() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return schoolService.getStudentByUsername(username)
                .map(student -> ResponseEntity.ok(schoolService.getCoursesForStudent(student.getId())))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/course-units")
    public ResponseEntity<List<CourseUnitDto>> courseUnits() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return schoolService.getStudentByUsername(username)
                .map(student -> ResponseEntity.ok(schoolService.getCourseUnitsForStudent(student.getId())))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/attendance")
    public ResponseEntity<List<AttendanceDto>> attendance() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return schoolService.getStudentByUsername(username)
                .map(student -> ResponseEntity.ok(schoolService.getAttendanceDtosForStudent(student.getId())))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Combined dashboard payload used by the frontend's student dashboard.
     * <p>
     * This used to assemble a handmade {@code Map<String,Object>} directly in the
     * controller (business logic that belongs in the service layer) and, worse, called
     * {@code Grade.getScore()} / {@code Attendance.getStatus()} - two leftover stub
     * methods that unconditionally threw {@code UnsupportedOperationException} - so this
     * endpoint 500'd on every single call. It's now a thin delegation to
     * {@link SchoolService#getStudentStatus}, which also computes a real fee balance
     * instead of the previous hardcoded "0.00".
     */
    @GetMapping("/my-status")
    public ResponseEntity<StudentStatusDto> getMyStatus() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return schoolService.getStudentByUsername(username)
                .map(student -> {
                    StudentStatusDto status = schoolService.getStudentStatus(student.getId());
                    var resultStatus = academicReportService.getStudentVisibleReport(student.getId());
                    status.setResultPublicationStatus(resultStatus.getPublicationStatus());
                    if (!Boolean.TRUE.equals(resultStatus.getPublished())) {
                        status.setGrades(List.of());
                        status.setCreditsEarned(null);
                        status.setAcademicStandingStatus("NOT_FINALIZED");
                        status.setMissingResultCourses(schoolService.getMissingResultCourseNames(student.getId()));
                    } else {
                        status.setCreditsEarned(resultStatus.getCreditsEarned());
                        status.setAcademicStandingStatus(resultStatus.getAcademicStandingStatus());
                        status.setMissingResultCourses(List.of());
                    }
                    return ResponseEntity.ok(status);
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
