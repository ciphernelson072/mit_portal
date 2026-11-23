package com.sis.controller;

import com.sis.dto.CreateFinancialHoldRequest;
import com.sis.entity.FinancialHold;
import com.sis.dto.AutomaticCourseAssignmentSummary;
import com.sis.service.AutomaticCourseAssignmentService;
import com.sis.service.FinancialHoldService;
import com.sis.service.SchoolService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/finance/holds")
public class FinancialHoldController {
    private final FinancialHoldService holdService;
    private final SchoolService schoolService;
    private final AutomaticCourseAssignmentService automaticCourseAssignments;

    public FinancialHoldController(FinancialHoldService holdService, SchoolService schoolService,
                                   AutomaticCourseAssignmentService automaticCourseAssignments) {
        this.holdService = holdService;
        this.schoolService = schoolService;
        this.automaticCourseAssignments = automaticCourseAssignments;
    }

    @PostMapping
    @PreAuthorize("@permissionSecurity.canManageFinancialHolds()")
    public ResponseEntity<FinancialHold> create(@Valid @RequestBody CreateFinancialHoldRequest request) {
        return ResponseEntity.status(201).body(holdService.create(request, username()));
    }

    @GetMapping("/mine")
    @PreAuthorize("hasAuthority('STUDENT')")
    public ResponseEntity<List<FinancialHold>> mine() {
        return schoolService.getStudentByUsername(username())
                .map(student -> ResponseEntity.ok(holdService.activeForStudent(student.getId())))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/release")
    @PreAuthorize("@permissionSecurity.canManageFinancialHolds()")
    public ResponseEntity<Map<String, Object>> release(@PathVariable Long id) {
        Long studentId = holdService.release(id, username());
        if (holdService.hasActiveHold(studentId)) {
            return ResponseEntity.ok(Map.of(
                    "message", "Financial hold released; additional active holds remain, so registration was not retried."));
        }
        AutomaticCourseAssignmentSummary assignments =
                automaticCourseAssignments.assignStudentForOpenTerms(studentId);
        return ResponseEntity.ok(Map.of(
                "message", "Final financial hold released; open-term registration was retried.",
                "automaticAssignments", assignments));
    }

    private String username() { return SecurityContextHolder.getContext().getAuthentication().getName(); }
}