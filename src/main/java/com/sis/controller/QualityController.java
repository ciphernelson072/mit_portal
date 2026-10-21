package com.sis.controller;

import com.sis.dto.*;
import com.sis.entity.AccreditationRecord;
import com.sis.entity.ProgramOutcome;
import com.sis.service.QualityService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/quality")
public class QualityController {
    private final QualityService quality;

    public QualityController(QualityService quality) { this.quality = quality; }

    @PostMapping("/outcomes")
    @PreAuthorize("@permissionSecurity.canReviewQuality()")
    public ResponseEntity<ProgramOutcome> createOutcome(@Valid @RequestBody CreateProgramOutcomeRequest request) { return ResponseEntity.status(201).body(quality.createOutcome(request)); }

    @GetMapping("/programs/{programId}/outcomes")
    @PreAuthorize("isAuthenticated()")
    public List<ProgramOutcome> outcomes(@PathVariable Long programId) { return quality.outcomes(programId); }

    @PostMapping("/accreditations")
    @PreAuthorize("@permissionSecurity.canReviewQuality()")
    public ResponseEntity<AccreditationRecord> createAccreditation(@Valid @RequestBody CreateAccreditationRequest request) { return ResponseEntity.status(201).body(quality.createAccreditation(request)); }

    @GetMapping("/accreditations/pending")
    @PreAuthorize("@permissionSecurity.canReviewQuality()")
    public List<AccreditationRecord> pending() { return quality.pendingAccreditations(); }

    @PostMapping("/accreditations/{id}/review")
    @PreAuthorize("@permissionSecurity.canReviewQuality()")
    public ResponseEntity<AccreditationRecord> review(@PathVariable Long id, @Valid @RequestBody ReviewAccreditationRequest request) { return ResponseEntity.ok(quality.review(id, request, username())); }

    @GetMapping("/programs/{programId}/accreditations")
    @PreAuthorize("isAuthenticated()")
    public List<AccreditationRecord> accreditations(@PathVariable Long programId) { return quality.accreditations(programId); }

    private String username() { return SecurityContextHolder.getContext().getAuthentication().getName(); }
}