package com.sis.controller;

import com.sis.dto.CreateExamPermitRequest;
import com.sis.dto.ExamReadinessDto;
import com.sis.dto.ExamReadinessItemDto;
import com.sis.dto.RejectionRequest;
import com.sis.entity.ExamPermit;
import com.sis.service.ExamReadinessService;
import com.sis.service.ExamPermitService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ExamPermitController {

    private final ExamPermitService examPermitService;
    private final ExamReadinessService examReadinessService;
    private final com.sis.service.SchoolService schoolService;

    public ExamPermitController(ExamPermitService examPermitService,
                                ExamReadinessService examReadinessService,
                                com.sis.service.SchoolService schoolService) {
        this.examPermitService = examPermitService;
        this.examReadinessService = examReadinessService;
        this.schoolService = schoolService;
    }

    @PostMapping("/student/exam-permits")
    @PreAuthorize("hasAuthority('STUDENT')")
    public ResponseEntity<Map<String, Object>> requestPermit(@Valid @RequestBody CreateExamPermitRequest body) {
        Long examId = body.getExamId();
        Long studentId = currentStudentId();
        ExamPermit permit = examPermitService.requestPermit(studentId, examId);
        return ResponseEntity.status(201).body(permitResponse(permit));
    }

    @GetMapping("/student/exam-permits")
    @PreAuthorize("hasAuthority('STUDENT')")
    public ResponseEntity<List<Map<String, Object>>> myPermits() {
        Long studentId = currentStudentId();
        return ResponseEntity.ok(examPermitService.listForStudent(studentId).stream()
                .map(this::permitResponse)
                .toList());
    }

    @GetMapping("/student/exam-readiness")
    @PreAuthorize("hasAuthority('STUDENT')")
    public ResponseEntity<List<ExamReadinessItemDto>> myExamReadiness() {
        return ResponseEntity.ok(examReadinessService.listForStudent(currentStudentId()));
    }

    @GetMapping("/admin/exam-permits/pending")
    @PreAuthorize("@permissionSecurity.canManageExamPermits()")
    public ResponseEntity<List<Map<String, Object>>> pendingPermits() {
        return ResponseEntity.ok(examPermitService.listPending().stream()
                .map(this::permitResponse)
                .toList());
    }

    @PostMapping("/admin/exam-permits/{permitId}/approve")
    @PreAuthorize("@permissionSecurity.canManageExamPermits()")
    public ResponseEntity<Map<String, Object>> approvePermit(@PathVariable Long permitId) {
        String approvedBy = SecurityContextHolder.getContext().getAuthentication().getName();
        ExamPermit permit = examPermitService.approvePermit(permitId, approvedBy);
        return ResponseEntity.ok(permitResponse(permit));
    }

    @PostMapping("/admin/exam-permits/{permitId}/reject")
    @PreAuthorize("@permissionSecurity.canManageExamPermits()")
    public ResponseEntity<Map<String, Object>> rejectPermit(@PathVariable Long permitId,
                                                            @Valid @RequestBody RejectionRequest body) {
        String rejectedBy = SecurityContextHolder.getContext().getAuthentication().getName();
        ExamPermit permit = examPermitService.rejectPermit(permitId, rejectedBy, body.getReason());
        return ResponseEntity.ok(permitResponse(permit));
    }

    private Long currentStudentId() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return schoolService.getStudentByUsername(username)
                .map(student -> student.getId())
                .orElseThrow(() -> new IllegalStateException("Student profile not found"));
    }

    private Map<String, Object> permitResponse(ExamPermit permit) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", permit.getId());
        payload.put("studentId", permit.getStudentId());
        payload.put("examId", permit.getExamId());
        payload.put("status", permit.getStatus().name());
        payload.put("requestedAt", permit.getRequestedAt());
        payload.put("approvedAt", permit.getApprovedAt());
        payload.put("approvedBy", permit.getApprovedBy());
        payload.put("rejectedAt", permit.getRejectedAt());
        payload.put("rejectedBy", permit.getRejectedBy());
        payload.put("reason", permit.getReason());
        ExamReadinessDto readiness = examReadinessService.assess(permit.getStudentId(), permit.getExamId());
        payload.put("readiness", readiness);
        return payload;
    }
}
