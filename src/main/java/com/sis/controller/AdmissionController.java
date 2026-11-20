package com.sis.controller;

import com.sis.dto.AdmissionDecisionRequest;
import com.sis.dto.AdmissionEnrollmentCredentialsResponse;
import com.sis.dto.CreateAdmittedStudentRequest;
import com.sis.dto.AdmissionDocumentRequirementRequest;
import com.sis.dto.EnrollAdmissionRequest;
import com.sis.entity.AdmissionApplicationStatus;
import com.sis.service.AdmissionService;
import com.sis.service.AdmissionDocumentService;
import com.sis.service.EmailService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ContentDisposition;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.context.SecurityContextHolder;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import java.util.Set;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/admissions")
@PreAuthorize("@permissionSecurity.canReviewAdmissions()")
public class AdmissionController {
    private static final Logger logger = Logger.getLogger(AdmissionController.class.getName());

    private final AdmissionService admissionService;
    private final EmailService emailService;
    private final AdmissionDocumentService admissionDocuments;

    public AdmissionController(
            AdmissionService admissionService, EmailService emailService,
            AdmissionDocumentService admissionDocuments) {
        this.admissionService = admissionService;
        this.emailService = emailService;
        this.admissionDocuments = admissionDocuments;
    }

    @GetMapping("/document-requirements")
    public List<com.sis.dto.AdmissionDocumentRequirementDto> documentRequirements() {
        return admissionDocuments.listRequirements(true);
    }

    @PostMapping("/document-requirements")
    public ResponseEntity<com.sis.dto.AdmissionDocumentRequirementDto> createDocumentRequirement(
            @Valid @RequestBody AdmissionDocumentRequirementRequest request) {
        return ResponseEntity.status(201).body(admissionDocuments.createRequirement(request));
    }

    @PatchMapping("/document-requirements/{id}/active")
    public ResponseEntity<com.sis.dto.AdmissionDocumentRequirementDto> setDocumentRequirementActive(
            @PathVariable Long id, @RequestParam boolean active) {
        return ResponseEntity.ok(admissionDocuments.setRequirementActive(id, active));
    }

    @GetMapping("/applications/{id}/documents")
    public List<com.sis.dto.AdmissionApplicationDocumentDto> applicationDocuments(@PathVariable Long id) {
        return admissionDocuments.listApplicationDocuments(id);
    }

    @GetMapping("/applications/{id}/documents/{documentId}/download")
    public ResponseEntity<Resource> downloadApplicationDocument(
            @PathVariable Long id, @PathVariable Long documentId) throws java.io.IOException {
        AdmissionDocumentService.AdmissionDocumentDownload document =
                admissionDocuments.download(id, documentId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(document.name(), StandardCharsets.UTF_8).build().toString())
                .body(new ByteArrayResource(document.content()));
    }

    @GetMapping("/applications")
    public List<Map<String, Object>> applications(@RequestParam(required = false) AdmissionApplicationStatus status) {
        Set<Long> enrolledApplications = admissionService.enrolledApplicationIds();
        return admissionService.list(status).stream()
                .map(application -> toResponse(application, enrolledApplications.contains(application.getId())))
                .toList();
    }

    @PatchMapping("/applications/{id}/decision")
    public ResponseEntity<Map<String, Object>> decide(@PathVariable Long id,
                                                       @Valid @RequestBody AdmissionDecisionRequest request) {
        return ResponseEntity.ok(toResponse(admissionService.decide(id, request)));
    }

    @PostMapping("/applications/{id}/enroll")
    public ResponseEntity<Map<String, Object>> enroll(@PathVariable Long id,
                                                       @Valid @RequestBody EnrollAdmissionRequest request) {
        String changedBy = SecurityContextHolder.getContext().getAuthentication().getName();
        com.sis.service.AdmissionEnrollmentResult result = admissionService.enroll(id, request, changedBy);
        return enrollmentResponse(result);
    }

    @PostMapping("/applications/{id}/enroll-new")
    public ResponseEntity<AdmissionEnrollmentCredentialsResponse> createStudentAndEnroll(@PathVariable Long id,
            @Valid @RequestBody CreateAdmittedStudentRequest request) {
        String changedBy = SecurityContextHolder.getContext().getAuthentication().getName();
        AdmissionEnrollmentCredentialsResponse result =
                admissionService.createStudentAndEnroll(id, request, changedBy);
        if (!emailService.isDeliveryConfigured()) {
            result.recordEmailDelivery(false, "Email is not configured; share the temporary password privately.");
        } else {
            try {
                String accountEmail = request.getEmail().trim().toLowerCase(Locale.ROOT);
                emailService.sendAdmissionCredentialsEmail(accountEmail, request.getFullName(),
                        result.getUsername(), result.getTemporaryPassword());
                result.recordEmailDelivery(true, "One-time sign-in details sent to the applicant's email.");
            } catch (IllegalStateException ex) {
                logger.warning("Admission sign-in email could not be delivered; using private admin handoff.");
                result.recordEmailDelivery(false,
                        "Email delivery failed; share the temporary password privately.");
            }
        }
        return ResponseEntity.status(201)
                .cacheControl(CacheControl.noStore())
                .body(result);
    }

    private ResponseEntity<Map<String, Object>> enrollmentResponse(
            com.sis.service.AdmissionEnrollmentResult result) {
        com.sis.entity.StudentProgram enrollment = result.enrollment();
        return ResponseEntity.status(201).body(Map.of(
                "id", enrollment.getId(),
                "studentId", enrollment.getStudent().getId(),
                "programId", enrollment.getProgram().getId(),
                "academicYearId", enrollment.getAcademicYear().getId(),
                "status", enrollment.getStatus(),
                "currentSemester", enrollment.getCurrentSemester(),
                "automaticAssignments", result.automaticAssignments()
        ));
    }

    @GetMapping("/applications/{id}/enrollment-candidates")
    public List<Map<String, Object>> enrollmentCandidates(@PathVariable Long id) {
        return admissionService.enrollmentCandidates(id);
    }

    private Map<String, Object> toResponse(com.sis.entity.AdmissionApplication application) {
        return toResponse(application, admissionService.isEnrolled(application));
    }

    private Map<String, Object> toResponse(com.sis.entity.AdmissionApplication application, boolean enrolled) {
        return Map.ofEntries(
                Map.entry("id", application.getId()),
                Map.entry("applicationNumber", application.getApplicationNumber()),
                Map.entry("applicantName", application.getApplicantName()),
                Map.entry("email", application.getEmail()),
                Map.entry("phone", application.getPhone() == null ? "" : application.getPhone()),
                Map.entry("programId", application.getProgram().getId()),
                Map.entry("programName", application.getProgram().getName()),
                Map.entry("academicYearId", application.getAcademicYear().getId()),
                Map.entry("academicYearName", application.getAcademicYear().getName()),
                Map.entry("status", application.getStatus()),
                Map.entry("enrolled", enrolled),
                Map.entry("reviewerNotes", application.getReviewerNotes() == null ? "" : application.getReviewerNotes()),
                Map.entry("submittedAt", application.getSubmittedAt()),
                Map.entry("decidedAt", application.getDecidedAt() == null ? "" : application.getDecidedAt())
        );
    }
}