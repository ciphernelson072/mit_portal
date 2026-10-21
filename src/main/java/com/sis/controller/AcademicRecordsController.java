package com.sis.controller;

import com.sis.service.AcademicRecordsService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import org.springframework.lang.Nullable;

@RestController
@RequestMapping("/api/academic-records")
public class AcademicRecordsController {
    private final AcademicRecordsService records;
    private final com.sis.service.PdfDocumentService pdfDocuments;

    public AcademicRecordsController(AcademicRecordsService records, com.sis.service.PdfDocumentService pdfDocuments) {
        this.records = records;
        this.pdfDocuments = pdfDocuments;
    }

    @GetMapping("/{studentId}/transcript")
    @PreAuthorize("@permissionSecurity.canManageAcademicOperations() or @studentAccessService.isOwnStudent(#studentId)")
    public Map<String, Object> transcript(@PathVariable Long studentId) { return records.transcript(studentId); }

    @GetMapping("/{studentId}/degree-audit")
    @PreAuthorize("@permissionSecurity.canManageAcademicOperations() or @studentAccessService.isOwnStudent(#studentId)")
    public Map<String, Object> degreeAudit(@PathVariable Long studentId,
                                           @RequestParam(required = false) @Nullable Long programId,
                                           @RequestParam(required = false) @Nullable Long academicYearId) {
        if ((programId == null) != (academicYearId == null)) {
            throw new IllegalArgumentException("Programme and academic year must be provided together.");
        }
        return programId == null
                ? records.degreeAudit(studentId)
                : records.degreeAudit(studentId, programId, academicYearId);
    }

    @GetMapping("/{studentId}/transcript/download")
    @PreAuthorize("@permissionSecurity.canManageAcademicOperations() or @studentAccessService.isOwnStudent(#studentId)")
    public ResponseEntity<byte[]> downloadTranscript(@PathVariable Long studentId) {
        Map<String, Object> transcript = records.transcript(studentId);
        try {
            java.util.List<String> lines = new java.util.ArrayList<>();
            lines.add("Student: " + transcript.get("studentName"));
            lines.add("Username: " + transcript.get("username"));
            lines.add("CGPA: " + transcript.get("cgpa"));
            lines.add("");
            for (Object entry : (java.util.List<?>) transcript.get("grades")) lines.add(entry.toString());
            byte[] pdf = pdfDocuments.report("OFFICIAL TRANSCRIPT", lines);
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=transcript-" + studentId + ".pdf")
                    .body(pdf);
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Unable to generate transcript", ex);
        }
    }
}