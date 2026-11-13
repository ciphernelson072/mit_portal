package com.sis.controller;

import com.sis.dto.CreateServiceRequest;
import com.sis.dto.ServiceRequestDto;
import com.sis.dto.UpdateServiceRequestStatus;
import com.sis.service.SchoolService;
import com.sis.service.ServiceRequestService;
import com.sis.service.PdfDocumentService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/student/requests")
public class ServiceRequestController {

    private static final Set<String> ADMIN_STATUSES = Set.of("OPEN", "IN_PROGRESS", "APPROVED", "REJECTED", "COMPLETED");

    private final SchoolService schoolService;
    private final ServiceRequestService requestService;
    private final PdfDocumentService pdfDocuments;

    public ServiceRequestController(SchoolService schoolService, ServiceRequestService requestService,
                                    PdfDocumentService pdfDocuments) {
        this.schoolService = schoolService;
        this.requestService = requestService;
        this.pdfDocuments = pdfDocuments;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('STUDENT') or hasAuthority('TEACHER')")
    public ResponseEntity<List<ServiceRequestDto>> list() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return schoolService.getUserByUsername(username)
                .map(user -> ResponseEntity.ok(requestService.listForUser(user.getId())))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('STUDENT') or hasAuthority('TEACHER')")
    public ResponseEntity<ServiceRequestDto> create(@Valid @RequestBody CreateServiceRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return schoolService.getUserByUsername(username)
            .map(user -> ResponseEntity.status(HttpStatus.CREATED)
                .body(schoolService.getStudentByUsername(username)
                    .map(student -> requestService.create(student, user, request.getType(), request.getSubject(), request.getDetails()))
                    .orElseGet(() -> requestService.create(user, request.getType(), request.getSubject(), request.getDetails()))))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/admin")
    @PreAuthorize("@permissionSecurity.canManageServiceRequests()")
    public ResponseEntity<List<ServiceRequestDto>> listAll() {
        return ResponseEntity.ok(requestService.listAll());
    }

    @GetMapping("/admin/{id}")
    @PreAuthorize("@permissionSecurity.canManageServiceRequests()")
    public ResponseEntity<ServiceRequestDto> getOne(@PathVariable Long id) {
        return requestService.findOne(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('STUDENT') or hasAuthority('TEACHER')")
    public ResponseEntity<ServiceRequestDto> getStudentRequest(@PathVariable Long id) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return schoolService.getUserByUsername(username)
                .flatMap(user -> requestService.findOneForUser(id, user.getId()))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("hasAuthority('STUDENT') or hasAuthority('TEACHER')")
    public ResponseEntity<byte[]> downloadStudentRequest(@PathVariable Long id) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return schoolService.getUserByUsername(username)
                .flatMap(user -> requestService.findOneForUser(id, user.getId()))
                .map(request -> {
                    try {
                        byte[] pdf = pdfDocuments.serviceRequest("REQ-" + request.getId(), request.getType(),
                                request.getSubject(), request.getDetails(), request.getStatus(), request.getCreatedAt());
                        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"request-" + id + ".pdf\"")
                                .body(pdf);
                    } catch (java.io.IOException e) {
                        throw new IllegalStateException("Unable to generate service request", e);
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/admin/{id}/status")
    @PreAuthorize("@permissionSecurity.canManageServiceRequests()")
    public ResponseEntity<ServiceRequestDto> updateStatus(@PathVariable Long id,
                                                           @Valid @RequestBody UpdateServiceRequestStatus request) {
        if (!ADMIN_STATUSES.contains(request.getStatus())) {
            return ResponseEntity.badRequest().build();
        }
        return requestService.updateStatus(id, request.getStatus(), SecurityContextHolder.getContext().getAuthentication().getName())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
