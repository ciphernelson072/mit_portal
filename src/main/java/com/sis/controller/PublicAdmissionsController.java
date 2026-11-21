package com.sis.controller;

import com.sis.dto.AdmissionApplicationRequest;
import com.sis.dto.AdmissionStatusRequest;
import com.sis.dto.AdmissionDocumentRequirementDto;
import com.sis.entity.AdmissionApplication;
import com.sis.repository.AcademicYearRepository;
import com.sis.repository.ProgramRepository;
import com.sis.service.AdmissionDocumentService;
import com.sis.service.AdmissionService;
import com.sis.service.EmailService;
import com.sis.service.PublicAdmissionSubmissionService;
import com.sis.service.TurnstileVerificationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

@RestController
@RequestMapping("/api/public/admissions")
public class PublicAdmissionsController {
    private static final Logger logger = Logger.getLogger(PublicAdmissionsController.class.getName());

    private final AdmissionService admissionService;
    private final ProgramRepository programRepository;
    private final AcademicYearRepository academicYearRepository;
    private final EmailService emailService;
    private final AdmissionDocumentService admissionDocuments;
    private final PublicAdmissionSubmissionService submissions;
    private final TurnstileVerificationService turnstile;

    public PublicAdmissionsController(AdmissionService admissionService, ProgramRepository programRepository,
                                      AcademicYearRepository academicYearRepository, EmailService emailService,
                                      AdmissionDocumentService admissionDocuments,
                                      PublicAdmissionSubmissionService submissions,
                                      TurnstileVerificationService turnstile) {
        this.admissionService = admissionService;
        this.programRepository = programRepository;
        this.academicYearRepository = academicYearRepository;
        this.emailService = emailService;
        this.admissionDocuments = admissionDocuments;
        this.submissions = submissions;
        this.turnstile = turnstile;
    }

    @GetMapping("/security-config")
    public Map<String, Object> securityConfig() {
        String siteKey = turnstile.getSiteKey();
        return Map.of("turnstileEnabled", turnstile.isEnabled(), "turnstileSiteKey", siteKey == null ? "" : siteKey);
    }

    @GetMapping("/programs")
    public List<Map<String, Object>> programs() {
        return programRepository.findActiveProgramsInActiveDirectoryOrderByName().stream()
                .map(program -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("id", program.getId());
                    result.put("code", program.getCode());
                    result.put("name", program.getName());
                    result.put("awardTitle", program.getAwardTitle());
                    result.put("durationYears", program.getDurationYears());
                    return result;
                })
                .toList();
    }

    @GetMapping("/academic-year")
    public Map<String, Object> currentAcademicYear() {
        return academicYearRepository.findFirstByActiveTrueOrderByStartDateDesc()
                .map(year -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("id", year.getId());
                    result.put("code", year.getCode());
                    result.put("name", year.getName());
                    return result;
                })
                .orElseGet(LinkedHashMap::new);
    }

    @GetMapping("/document-requirements")
    public List<AdmissionDocumentRequirementDto> documentRequirements() {
        return admissionDocuments.listRequirements(false);
    }

    @PostMapping("/applications")
    public ResponseEntity<Map<String, Object>> submit(@Valid @RequestBody AdmissionApplicationRequest request) {
        turnstile.verify(request.getTurnstileToken(), "admission_submit");
        if (admissionDocuments.hasActiveRequiredDocuments()) {
            throw new IllegalStateException(
                    "Upload the required admission documents using the application form.");
        }
        AdmissionApplication application = admissionService.submit(request);
        return applicationResponse(application);
    }

    @PostMapping(value = "/applications", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> submitWithDocuments(
            @Valid @ModelAttribute AdmissionApplicationRequest request,
            MultipartHttpServletRequest multipartRequest) {
        turnstile.verify(request.getTurnstileToken(), "admission_submit");
        AdmissionApplication application = submissions.submit(request, multipartRequest.getFileMap());
        return applicationResponse(application);
    }

    private ResponseEntity<Map<String, Object>> applicationResponse(AdmissionApplication application) {
        boolean emailSent = false;
        String emailMessage;
        if (!emailService.isDeliveryConfigured()) {
            emailMessage = "Application saved. Email confirmation could not be sent because email delivery is not configured.";
        } else {
            try {
                emailService.sendApplicationReceivedEmail(
                        application.getEmail(),
                        application.getApplicantName(),
                        application.getApplicationNumber(),
                        application.getProgram().getName(),
                        application.getAcademicYear().getName());
                emailSent = true;
                emailMessage = "Application saved. A confirmation email has been sent; check your inbox and spam folder.";
            } catch (IllegalStateException ex) {
                logger.warning("Admission receipt email could not be delivered; application remains saved.");
                emailMessage = "Application saved, but the confirmation email could not be sent. Keep your application number shown on this page.";
            }
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "applicationNumber", application.getApplicationNumber(),
                "status", application.getStatus(),
                "message", "Application submitted successfully",
                "emailSent", emailSent,
                "emailMessage", emailMessage
        ));
    }

    @PostMapping("/applications/status")
    public Map<String, Object> status(@Valid @RequestBody AdmissionStatusRequest request) {
        turnstile.verify(request.getTurnstileToken(), "application_status");
        AdmissionApplication application = admissionService.findStatus(request);
        return Map.of(
                "applicationNumber", application.getApplicationNumber(),
                "program", application.getProgram().getName(),
                "academicYear", application.getAcademicYear().getName(),
                "status", application.getStatus()
        );
    }
}