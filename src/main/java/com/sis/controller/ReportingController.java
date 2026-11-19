package com.sis.controller;

import com.sis.dto.AcademicReportDto;
import com.sis.dto.ReportExportRequest;
import com.sis.dto.PublishAcademicReportRequest;
import com.sis.service.AcademicReportService;
import com.sis.service.PdfDocumentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

/**
 * Reporting Controller - Generates academic and statistical reports
 * Endpoints for academic performance, statistics, and comparisons
 */
@RestController
@RequestMapping("/api/reports")
public class ReportingController {

    @Autowired
    private AcademicReportService academicReportService;

    @Autowired
    private PdfDocumentService pdfDocuments;

    /**
     * Generate comprehensive academic report for student
     * @param studentId student ID
     * @return academic report DTO
     */
    @GetMapping("/academic/{studentId}")
    @PreAuthorize("@permissionSecurity.canViewInstitutionalReports() or @studentAccessService.isOwnStudent(#studentId) or @teacherAccessService.teachesStudent(#studentId)")
    public ResponseEntity<AcademicReportDto> generateAcademicReport(@PathVariable Long studentId) {
        AcademicReportDto report = isStudentSession()
                ? academicReportService.getStudentVisibleReport(studentId)
                : academicReportService.generateAcademicReport(studentId);
        return ResponseEntity.ok(report);
    }

    @GetMapping("/academic/{studentId}/download")
    @PreAuthorize("@permissionSecurity.canViewInstitutionalReports() or @studentAccessService.isOwnStudent(#studentId) or @teacherAccessService.teachesStudent(#studentId)")
    public ResponseEntity<byte[]> downloadAcademicReport(@PathVariable Long studentId) {
        if (!academicReportService.isOfficiallyPublished(studentId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        try {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"academic-report-" + studentId + ".pdf\"")
                    .body(pdfDocuments.academicReport(academicReportService.getPublishedAcademicReport(studentId)));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Unable to generate academic report", e);
        }
    }

    @GetMapping("/academic/{studentId}/official/download")
    @PreAuthorize("@permissionSecurity.canApproveStudentReports() or @permissionSecurity.canViewInstitutionalReports() or @studentAccessService.isOwnStudent(#studentId)")
    public ResponseEntity<byte[]> downloadOfficialAcademicReport(@PathVariable Long studentId) {
        if (!academicReportService.isOfficiallyPublished(studentId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .build();
        }
        try {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"official-academic-report-" + studentId + ".pdf\"")
                    .body(pdfDocuments.academicReport(academicReportService.getPublishedAcademicReport(studentId)));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Unable to generate official academic report", e);
        }
    }

    @GetMapping("/academic/{studentId}/status")
    @PreAuthorize("@permissionSecurity.canApproveStudentReports() or @permissionSecurity.canViewInstitutionalReports() or @studentAccessService.isOwnStudent(#studentId)")
    public ResponseEntity<AcademicReportDto> academicReportStatus(@PathVariable Long studentId) {
        AcademicReportDto report = isStudentSession()
                ? academicReportService.getStudentVisibleReport(studentId)
                : academicReportService.getApprovalStatus(studentId);
        return ResponseEntity.ok(report);
    }

    @PostMapping("/academic/{studentId}/approve")
    @PreAuthorize("@permissionSecurity.canApproveStudentReports()")
    public ResponseEntity<AcademicReportDto> approveAcademicReport(@PathVariable Long studentId) {
        String approver = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(academicReportService.approveAcademicReport(studentId, approver));
    }

    @PostMapping("/academic/{studentId}/publish")
    @PreAuthorize("@permissionSecurity.canApproveStudentReports()")
    public ResponseEntity<AcademicReportDto> publishAcademicReport(
            @PathVariable Long studentId,
            @Valid @RequestBody PublishAcademicReportRequest request) {
        String publisher = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(academicReportService.publishAcademicReport(
                studentId, request.getResultsVersion(), publisher));
    }

    private boolean isStudentSession() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "STUDENT".equals(authority.getAuthority()));
    }

    /**
     * Get class-wise academic statistics
     * @param className class name
     * @return statistics report
     */
    @GetMapping("/class/{className}")
    @PreAuthorize("@permissionSecurity.canViewInstitutionalReports()")
    public ResponseEntity<String> getClassStatistics(@PathVariable String className) {
        String statistics = academicReportService.getClassStatistics(className);
        return ResponseEntity.ok(statistics);
    }

    /**
     * Get teacher performance statistics
     * @param teacherId teacher ID
     * @return statistics report
     */
    @GetMapping("/teacher/{teacherId}")
    @PreAuthorize("@permissionSecurity.canViewInstitutionalReports() or @teacherAccessService.isOwnTeacher(#teacherId)")
    public ResponseEntity<String> getTeacherStatistics(@PathVariable Long teacherId) {
        String statistics = academicReportService.getTeacherStatistics(teacherId);
        return ResponseEntity.ok(statistics);
    }

    /**
     * Generate comparison report between two students
     * @param studentId1 first student ID
     * @param studentId2 second student ID
     * @return comparison report
     */
    @GetMapping("/compare")
    @PreAuthorize("@permissionSecurity.canViewInstitutionalReports()")
    public ResponseEntity<String> compareStudents(
            @RequestParam Long studentId1,
            @RequestParam Long studentId2) {
        String comparison = academicReportService.generateComparisonReport(studentId1, studentId2);
        return ResponseEntity.ok(comparison);
    }

    /**
     * Get top performing students in class
     * @param className class name
     * @param limit number of top students (default 10)
     * @return top students report
     */
    @GetMapping("/top-students")
    @PreAuthorize("@permissionSecurity.canViewInstitutionalReports()")
    public ResponseEntity<String> getTopStudents(
            @RequestParam String className,
            @RequestParam(defaultValue = "10") int limit) {
        String topStudents = academicReportService.getTopStudents(className, limit);
        return ResponseEntity.ok(topStudents);
    }

    @PostMapping("/export")
    @PreAuthorize("@permissionSecurity.canViewInstitutionalReports()")
    public ResponseEntity<byte[]> exportReport(@Valid @RequestBody ReportExportRequest request) {
        try {
            byte[] pdf = pdfDocuments.report(request.getTitle(), request.getLines());
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"report.pdf\"")
                    .body(pdf);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Unable to generate report PDF", e);
        }
    }
}
