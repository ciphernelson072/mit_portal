package com.sis.controller;

import com.sis.dto.CreateGradeAppealRequest;
import com.sis.dto.GradeAppealDecisionRequest;
import com.sis.dto.GradeAppealDto;
import com.sis.dto.GradeAppealTeacherResponse;
import com.sis.exception.ResourceNotFoundException;
import com.sis.service.GradeAppealService;
import com.sis.service.SchoolService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class GradeAppealController {

    private final GradeAppealService appeals;
    private final SchoolService school;

    public GradeAppealController(GradeAppealService appeals, SchoolService school) {
        this.appeals = appeals;
        this.school = school;
    }

    @GetMapping("/api/student/grade-appeals")
    @PreAuthorize("hasAuthority('STUDENT')")
    public ResponseEntity<List<GradeAppealDto>> studentAppeals() {
        return ResponseEntity.ok(appeals.listForStudent(username()));
    }

    @PostMapping("/api/student/grade-appeals")
    @PreAuthorize("hasAuthority('STUDENT')")
    public ResponseEntity<GradeAppealDto> submitAppeal(@Valid @RequestBody CreateGradeAppealRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                appeals.submit(request.getGradeId(), request.getReason(), username()));
    }

    @GetMapping("/api/admin/grade-appeals")
    @PreAuthorize("@permissionSecurity.canManageServiceRequests()")
    public ResponseEntity<List<GradeAppealDto>> allAppeals() {
        return ResponseEntity.ok(appeals.listAll());
    }

    @PostMapping("/api/admin/grade-appeals/{appealId}/teacher-consultation")
    @PreAuthorize("@permissionSecurity.canManageServiceRequests()")
    public ResponseEntity<GradeAppealDto> requestTeacherConsultation(@PathVariable Long appealId) {
        return ResponseEntity.ok(appeals.requestTeacherConsultation(appealId, username()));
    }

    @PostMapping("/api/admin/grade-appeals/{appealId}/confirm-teacher-consultation")
    @PreAuthorize("@permissionSecurity.canManageServiceRequests()")
    public ResponseEntity<GradeAppealDto> confirmTeacherConsultation(@PathVariable Long appealId) {
        return ResponseEntity.ok(appeals.confirmTeacherConsultation(appealId, username()));
    }

    @PostMapping("/api/admin/grade-appeals/{appealId}/decision")
    @PreAuthorize("@permissionSecurity.canReviewGradeApprovals()")
    public ResponseEntity<GradeAppealDto> decide(@PathVariable Long appealId,
                                                  @Valid @RequestBody GradeAppealDecisionRequest request) {
        return ResponseEntity.ok(appeals.decide(
                appealId, request.getDecision(), request.getNote(), username()));
    }

    @GetMapping("/api/teacher/grade-appeals")
    @PreAuthorize("hasAuthority('TEACHER') or hasAuthority('ADMIN')")
    public ResponseEntity<List<GradeAppealDto>> teacherAppeals() {
        String username = username();
        var teacher = school.getTeacherByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher profile not found."));
        List<Long> courseIds = school.getCoursesForTeacher(teacher.getId()).stream()
                .map(com.sis.dto.CourseDto::getId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        return ResponseEntity.ok(appeals.listForTeacherCourses(courseIds));
    }

    @PostMapping("/api/teacher/grade-appeals/{appealId}/response")
    @PreAuthorize("hasAuthority('TEACHER')")
    public ResponseEntity<GradeAppealDto> teacherResponse(
            @PathVariable Long appealId, @Valid @RequestBody GradeAppealTeacherResponse request) {
        return ResponseEntity.ok(appeals.respondAsTeacher(appealId, request.getResponse(), username()));
    }

    private String username() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
