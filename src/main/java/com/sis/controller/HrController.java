package com.sis.controller;

import com.sis.dto.*;
import com.sis.entity.LeaveRequest;
import com.sis.entity.Staff;
import com.sis.service.HrService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/hr")
public class HrController {
    private final HrService hr;

    public HrController(HrService hr) { this.hr = hr; }

    @PostMapping("/staff")
    @PreAuthorize("@permissionSecurity.canManageHumanResources()")
    public ResponseEntity<Staff> createStaff(@Valid @RequestBody CreateStaffRequest request) {
        return ResponseEntity.status(201).body(hr.createStaff(request));
    }

    @GetMapping("/staff")
    @PreAuthorize("@permissionSecurity.canManageHumanResources()")
    public List<Staff> staff() { return hr.listStaff(); }

    @PostMapping("/leave")
    @PreAuthorize("@permissionSecurity.canRequestLeave()")
    public ResponseEntity<LeaveRequest> requestLeave(@Valid @RequestBody CreateLeaveRequest request) {
        return ResponseEntity.status(201).body(hr.requestLeave(username(), request));
    }

    @GetMapping("/leave/mine")
    @PreAuthorize("@permissionSecurity.canRequestLeave()")
    public List<LeaveRequest> mine() { return hr.leavesForStaff(username()); }

    @GetMapping("/leave/pending")
    @PreAuthorize("@permissionSecurity.canManageHumanResources()")
    public List<LeaveRequest> pending() { return hr.pendingLeaves(); }

    @PostMapping("/leave/{id}/review")
    @PreAuthorize("@permissionSecurity.canManageHumanResources()")
    public ResponseEntity<LeaveRequest> review(@PathVariable Long id, @Valid @RequestBody ReviewLeaveRequest request) {
        return ResponseEntity.ok(hr.reviewLeave(id, request, username()));
    }

    private String username() { return SecurityContextHolder.getContext().getAuthentication().getName(); }
}