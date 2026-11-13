package com.sis.controller;

import com.sis.dto.AdmissionDecisionRequest;
import com.sis.dto.AdmissionEnrollmentCredentialsResponse;
import com.sis.dto.CreateAdmittedStudentRequest;
import com.sis.dto.EnrollAdmissionRequest;
import com.sis.entity.AdmissionApplicationStatus;
import com.sis.service.AdmissionService;
import com.sis.service.EmailService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.Set;

@RestController
@RequestMapping("/api/admissions")
@PreAuthorize("@permissionSecurity.canReviewAdmissions()")
public class AdmissionController {
    private static final Logger logger = Logger.getLogger(AdmissionController.class.getName());

    private final AdmissionService admissionService;
    private final EmailService emailService;

    public AdmissionController(AdmissionService admissionService, EmailService emailService) {
        this.admissionService = admissionService;
        this.emailService = emailService;
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
        com.sis.entity.StudentProgram enrollment = admissionService.enroll(id, request, changedBy);
        return enrollmentResponse(enrollment);
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
                emailService.sendAdmissionCredentialsEmail(request.getEmail(), request.getFullName(),
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

    private ResponseEntity<Map<String, Object>> enrollmentResponse(com.sis.entity.StudentProgram enrollment) {
        return ResponseEntity.status(201).body(Map.of(
                "id", enrollment.getId(),
                "studentId", enrollment.getStudent().getId(),
                "programId", enrollment.getProgram().getId(),
                "academicYearId", enrollment.getAcademicYear().getId(),
                "status", enrollment.getStatus()
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