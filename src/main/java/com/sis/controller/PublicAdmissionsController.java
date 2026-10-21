package com.sis.controller;

import com.sis.dto.AdmissionApplicationRequest;
import com.sis.dto.AdmissionStatusRequest;
import com.sis.entity.AdmissionApplication;
import com.sis.repository.AcademicYearRepository;
import com.sis.repository.ProgramRepository;
import com.sis.service.AdmissionService;
import com.sis.service.EmailService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
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

    public PublicAdmissionsController(AdmissionService admissionService, ProgramRepository programRepository,
                                      AcademicYearRepository academicYearRepository, EmailService emailService) {
        this.admissionService = admissionService;
        this.programRepository = programRepository;
        this.academicYearRepository = academicYearRepository;
        this.emailService = emailService;
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

    @PostMapping("/applications")
    public ResponseEntity<Map<String, Object>> submit(@Valid @RequestBody AdmissionApplicationRequest request) {
        AdmissionApplication application = admissionService.submit(request);
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
        AdmissionApplication application = admissionService.findStatus(request);
        return Map.of(
                "applicationNumber", application.getApplicationNumber(),
                "program", application.getProgram().getName(),
                "academicYear", application.getAcademicYear().getName(),
                "status", application.getStatus()
        );
    }
}