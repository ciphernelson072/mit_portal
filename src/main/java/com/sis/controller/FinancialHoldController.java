package com.sis.controller;

import com.sis.dto.CreateFinancialHoldRequest;
import com.sis.entity.FinancialHold;
import com.sis.service.FinancialHoldService;
import com.sis.service.SchoolService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/finance/holds")
public class FinancialHoldController {
    private final FinancialHoldService holdService;
    private final SchoolService schoolService;

    public FinancialHoldController(FinancialHoldService holdService, SchoolService schoolService) {
        this.holdService = holdService;
        this.schoolService = schoolService;
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
    public ResponseEntity<Void> release(@PathVariable Long id) {
        holdService.release(id, username());
        return ResponseEntity.noContent().build();
    }

    private String username() { return SecurityContextHolder.getContext().getAuthentication().getName(); }
}