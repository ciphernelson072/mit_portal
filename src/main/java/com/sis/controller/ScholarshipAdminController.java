package com.sis.controller;

import com.sis.dto.CreateScholarshipRequest;
import com.sis.dto.ReviewScholarshipApplicationRequest;
import com.sis.dto.ScholarshipApplicationAdminDto;
import com.sis.entity.Scholarship;
import com.sis.entity.ScholarshipApplication;
import com.sis.service.ScholarshipService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/admin/scholarships")
@PreAuthorize("hasAuthority('ADMIN')")
public class ScholarshipAdminController {
    private final ScholarshipService scholarships;

    public ScholarshipAdminController(ScholarshipService scholarships) {
        this.scholarships = scholarships;
    }

    @GetMapping
    public List<Scholarship> list() {
        return scholarships.listForAdministration();
    }

    @PostMapping
    public ResponseEntity<Scholarship> create(@Valid @RequestBody CreateScholarshipRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(scholarships.create(request));
    }

    @PatchMapping("/{id}/availability")
    public Scholarship setAvailability(@PathVariable Long id, @RequestParam boolean open) {
        return scholarships.setAvailability(id, open);
    }

    @GetMapping("/applications")
    public List<ScholarshipApplicationAdminDto> applications(@RequestParam(required = false) Long scholarshipId) {
        return scholarships.listApplications(scholarshipId);
    }

    @PatchMapping("/applications/{id}/status")
    public ScholarshipApplication review(@PathVariable Long id,
                                         @Valid @RequestBody ReviewScholarshipApplicationRequest request) {
        return scholarships.reviewApplication(id, request.getStatus());
    }
}
