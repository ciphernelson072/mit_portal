package com.sis.controller;

import com.sis.dto.*;
import com.sis.entity.ResearchProject;
import com.sis.security.PermissionSecurity;
import com.sis.service.ResearchService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/research")
public class ResearchController {
    private final ResearchService research;
    private final PermissionSecurity permissionSecurity;

    public ResearchController(ResearchService research, PermissionSecurity permissionSecurity) {
        this.research = research;
        this.permissionSecurity = permissionSecurity;
    }

    @GetMapping("/supervisors")
    @PreAuthorize("hasAuthority('STUDENT')")
    public List<ResearchSupervisorDto> supervisors() { return research.availableSupervisors(); }

    @PostMapping("/projects")
    @PreAuthorize("hasAuthority('STUDENT')")
    public ResponseEntity<ResearchProject> createProject(@Valid @RequestBody CreateResearchProjectRequest request) { return ResponseEntity.status(201).body(research.createProject(username(), request)); }

    @GetMapping("/projects/mine")
    @PreAuthorize("hasAuthority('STUDENT')")
    public List<ResearchProject> mine() { return research.mine(username()); }

    @GetMapping("/projects/pending")
    @PreAuthorize("@permissionSecurity.canReviewResearch()")
    public List<ResearchProject> pendingProjects() { return research.pendingProjects(); }

    @PostMapping("/projects/{id}/review")
    @PreAuthorize("@permissionSecurity.canReviewResearch()")
    public ResponseEntity<ResearchProject> reviewProject(@PathVariable Long id, @Valid @RequestBody ReviewResearchProjectRequest request) { return ResponseEntity.ok(research.reviewProject(id, request, username())); }

    @GetMapping("/theses/mine")
    @PreAuthorize("hasAuthority('STUDENT')")
    public List<ThesisSubmissionDto> mineTheses() { return research.mineTheses(username()); }

    @PostMapping(value = "/theses", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('STUDENT')")
    public ResponseEntity<ThesisSubmissionDto> submitThesis(
            @RequestParam Long researchProjectId,
            @RequestParam(required = false) String notes,
            @RequestParam MultipartFile file) throws IOException {
        if (notes != null && notes.length() > 2000) {
            throw new IllegalArgumentException("Thesis notes must be 2,000 characters or fewer");
        }
        return ResponseEntity.status(201).body(research.submitThesis(username(), researchProjectId, notes, file));
    }

    @GetMapping("/theses/pending")
    @PreAuthorize("@permissionSecurity.canReviewResearch()")
    public List<ThesisSubmissionDto> pendingTheses() { return research.pendingTheses(); }

    @PostMapping("/theses/{id}/review")
    @PreAuthorize("@permissionSecurity.canReviewResearch()")
    public ResponseEntity<ThesisSubmissionDto> reviewThesis(@PathVariable Long id, @Valid @RequestBody ReviewThesisSubmissionRequest request) { return ResponseEntity.ok(research.reviewThesis(id, request, username())); }

    @GetMapping("/theses/{id}/download")
    @PreAuthorize("hasAuthority('STUDENT') or @permissionSecurity.canReviewResearch()")
    public ResponseEntity<Resource> downloadThesis(@PathVariable Long id) throws IOException {
        byte[] content = research.downloadThesis(username(), id, permissionSecurity.canReviewResearch());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"thesis-submission-" + id + ".pdf\"")
                .body(new ByteArrayResource(content));
    }

    private String username() { return SecurityContextHolder.getContext().getAuthentication().getName(); }
}