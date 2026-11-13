package com.sis.controller;

import com.sis.dto.StudentDocumentDto;
import com.sis.service.SchoolService;
import com.sis.service.StudentDocumentService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/student/documents")
@PreAuthorize("hasAuthority('STUDENT')")
public class StudentDocumentController {
    private final SchoolService schoolService;
    private final StudentDocumentService documentService;

    public StudentDocumentController(SchoolService schoolService, StudentDocumentService documentService) {
        this.schoolService = schoolService;
        this.documentService = documentService;
    }

    @GetMapping
    public ResponseEntity<List<StudentDocumentDto>> list() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return schoolService.getStudentByUsername(username)
                .map(student -> ResponseEntity.ok(documentService.list(student.getId())))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<StudentDocumentDto> upload(@RequestParam String name, @RequestParam MultipartFile file) throws IOException {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return schoolService.getStudentByUsername(username)
                .map(student -> {
                    try {
                        return ResponseEntity.ok(documentService.upload(student, name, file));
                    } catch (IOException e) {
                        throw new IllegalArgumentException("Unable to store document", e);
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) throws IOException {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return schoolService.getStudentByUsername(username)
                .map(student -> {
                    try {
                        StudentDocumentDto document = documentService.find(id, student.getId());
                        if (document == null) {
                            return ResponseEntity.notFound().<Resource>build();
                        }
                        byte[] content = documentService.download(id, student.getId());
                        String contentType = resolveContentType(document.getName());
                        String filename = document.getName() == null || document.getName().isBlank() ? "document" : document.getName();
                        return ResponseEntity.ok()
                                .contentType(MediaType.parseMediaType(contentType))
                                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                                .body((Resource) new ByteArrayResource(content));
                    } catch (IOException e) {
                        return ResponseEntity.notFound().<Resource>build();
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private String resolveContentType(String fileName) {
        if (fileName == null) return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf")) return MediaType.APPLICATION_PDF_VALUE;
        if (lower.endsWith(".png")) return MediaType.IMAGE_PNG_VALUE;
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return MediaType.IMAGE_JPEG_VALUE;
        if (lower.endsWith(".gif")) return MediaType.IMAGE_GIF_VALUE;
        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return schoolService.getStudentByUsername(username)
                .filter(student -> documentService.delete(id, student.getId()))
                .map(student -> ResponseEntity.ok().<Void>build())
                .orElse(ResponseEntity.notFound().build());
    }
}
