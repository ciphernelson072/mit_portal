package com.sis.controller;

import com.sis.dto.AllocateHostelRequest;
import com.sis.dto.CreateHostelRoomRequest;
import com.sis.dto.HostelAllocationDto;
import com.sis.entity.HostelApplication;
import com.sis.entity.HostelAllocation;
import com.sis.entity.HostelRoom;
import com.sis.service.AccommodationService;
import com.sis.service.SchoolService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/accommodation")
public class AccommodationController {
    private final AccommodationService accommodation;
    private final SchoolService schoolService;

    public AccommodationController(AccommodationService accommodation, SchoolService schoolService) {
        this.accommodation = accommodation;
        this.schoolService = schoolService;
    }

    @GetMapping("/rooms")
    @PreAuthorize("hasAuthority('STUDENT') or @permissionSecurity.canManageAccommodation()")
    public List<HostelRoom> rooms() { return accommodation.rooms(); }

    @PostMapping("/rooms")
    @PreAuthorize("@permissionSecurity.canManageAccommodation()")
    public ResponseEntity<HostelRoom> createRoom(@Valid @RequestBody CreateHostelRoomRequest request) {
        return ResponseEntity.status(201).body(accommodation.createRoom(request));
    }

    @GetMapping("/applications/pending")
    @PreAuthorize("@permissionSecurity.canManageAccommodation()")
    public List<HostelApplication> pendingApplications() {
        return accommodation.pendingApplications();
    }

    @PostMapping("/allocations")
    @PreAuthorize("@permissionSecurity.canManageAccommodation()")
    public ResponseEntity<HostelAllocation> allocate(@Valid @RequestBody AllocateHostelRequest request) {
        return ResponseEntity.status(201).body(accommodation.allocate(request));
    }

    @GetMapping("/allocations/mine")
    @PreAuthorize("hasAuthority('STUDENT')")
    public ResponseEntity<List<HostelAllocationDto>> mine() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return schoolService.getStudentByUsername(username)
                .map(student -> ResponseEntity.ok(accommodation.studentAllocations(student.getId())))
                .orElse(ResponseEntity.notFound().build());
    }
}