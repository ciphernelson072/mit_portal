package com.sis.controller;

import com.sis.dto.AdminFileListingDto;
import com.sis.service.FileUploadService;
import com.sis.service.StudentService;
import com.sis.service.TeacherService;
import com.sis.entity.Student;
import com.sis.entity.Teacher;
import com.sis.exception.ResourceNotFoundException;
import com.sis.repository.AccreditationRecordRepository;
import com.sis.repository.CourseMaterialRepository;
import com.sis.repository.StudentDocumentRepository;
import com.sis.repository.StudentRepository;
import com.sis.repository.TeacherRepository;
import com.sis.repository.ThesisSubmissionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.logging.Logger;

/**
 * File Upload Controller - Handles profile image uploads and downloads
 * Manages student and teacher profile images with file validation and storage
 */
@RestController
@RequestMapping("/api/files")
public class FileUploadController {

    private static final Logger logger = Logger.getLogger(FileUploadController.class.getName());

    @Autowired
    private FileUploadService fileUploadService;

    @Autowired
    private StudentService studentService;

    @Autowired
    private TeacherService teacherService;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private TeacherRepository teacherRepository;

    @Autowired
    private StudentDocumentRepository studentDocumentRepository;

    @Autowired
    private ThesisSubmissionRepository thesisSubmissionRepository;

    @Autowired
    private CourseMaterialRepository courseMaterialRepository;

    @Autowired
    private AccreditationRecordRepository accreditationRecordRepository;

    @GetMapping("/admin/list")
    @PreAuthorize("@permissionSecurity.canManagePortalSettings()")
    public ResponseEntity<AdminFileListingDto> listStoredFiles() throws IOException {
        return ResponseEntity.ok(fileUploadService.listStoredFiles());
    }

    /**
     * Upload student profile image
     * @param studentId student ID
     * @param file image file to upload
     * @return success response with file path
     */
    @PostMapping("/student/{studentId}/profile-image")
    @PreAuthorize("hasAuthority('ADMIN') or @studentAccessService.isOwnStudent(#studentId)")
    public ResponseEntity<String> uploadStudentProfileImage(
            @PathVariable Long studentId,
            @RequestParam("file") MultipartFile file) {
        try {
            Student student = studentService.getStudentById(studentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Student not found"));

            String previousImagePath = student.getProfileImagePath();
            String imagePath = fileUploadService.uploadStudentProfileImage(file, studentId);

            student.setProfileImagePath(imagePath);
            studentService.updateStudent(studentId, student);
            if (previousImagePath != null && !previousImagePath.equals(imagePath)
                    && !fileUploadService.deleteProfileImage(previousImagePath)) {
                logger.warning("New profile image saved, but previous image could not be removed for student: " + studentId);
            }

            logger.info("Profile image uploaded for student: " + studentId);
            return ResponseEntity.ok("Profile image uploaded successfully. Path: " + imagePath);
        } catch (IOException e) {
            logger.severe("Error uploading profile image: " + e.getMessage());
            return ResponseEntity.badRequest().body("Error uploading file: " + e.getMessage());
        }
    }

    /**
     * Upload teacher profile image
     * @param teacherId teacher ID
     * @param file image file to upload
     * @return success response with file path
     */
    @PostMapping("/teacher/{teacherId}/profile-image")
    @PreAuthorize("hasAuthority('ADMIN') or @teacherAccessService.isOwnTeacher(#teacherId)")
    public ResponseEntity<String> uploadTeacherProfileImage(
            @PathVariable Long teacherId,
            @RequestParam("file") MultipartFile file) {
        try {
            Teacher teacher = teacherService.getTeacherById(teacherId)
                    .orElseThrow(() -> new ResourceNotFoundException("Teacher not found"));

            String previousImagePath = teacher.getProfileImagePath();
            String imagePath = fileUploadService.uploadTeacherProfileImage(file, teacherId);
            teacher.setProfileImagePath(imagePath);
            teacherService.updateTeacher(teacherId, teacher);
            if (previousImagePath != null && !previousImagePath.equals(imagePath)
                    && !fileUploadService.deleteProfileImage(previousImagePath)) {
                logger.warning("New profile image saved, but previous image could not be removed for teacher: " + teacherId);
            }

            logger.info("Profile image uploaded for teacher: " + teacherId);
            return ResponseEntity.ok("Profile image uploaded successfully. Path: " + imagePath);
        } catch (IOException e) {
            logger.severe("Error uploading profile image: " + e.getMessage());
            return ResponseEntity.badRequest().body("Error uploading file: " + e.getMessage());
        }
    }

    /**
     * Download profile image
     * @param filePath relative file path
     * @return file as binary data
     */
    @GetMapping("/download")
    @PreAuthorize("hasAuthority('ADMIN') or @fileAccessService.isOwnFile(#filePath)")
    public ResponseEntity<Resource> downloadFile(@RequestParam String filePath) {
        try {
            byte[] fileContent = fileUploadService.getFileAsBytes(filePath);
            
            String filename = filePath.substring(filePath.lastIndexOf("/") + 1);
            String contentType = getContentType(filename);

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                    .body(new InputStreamResource(new org.springframework.core.io.ByteArrayResource(fileContent).getInputStream()));
        } catch (IOException e) {
            logger.severe("Error downloading file: " + e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Delete profile image
     * @param filePath relative file path
     * @return success response
     */
    @DeleteMapping("/delete")
    @PreAuthorize("hasAuthority('ADMIN') or @permissionSecurity.canManagePortalSettings()")
    public ResponseEntity<?> deleteFile(@RequestParam String filePath) {
        String normalizedPath = fileUploadService.normalizeRelativePath(filePath);
        if (isFileInUse(normalizedPath)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "This file is still referenced by a portal record and cannot be deleted."));
        }
        boolean deleted = fileUploadService.deleteProfileImage(normalizedPath);
        if (deleted) {
            return ResponseEntity.ok("File deleted successfully");
        } else {
            return ResponseEntity.badRequest().body("Error deleting file");
        }
    }

    private boolean isFileInUse(String filePath) {
        return studentRepository.existsByProfileImagePath(filePath)
                || teacherRepository.existsByProfileImagePath(filePath)
                || studentDocumentRepository.existsByFilePath(filePath)
                || thesisSubmissionRepository.existsByFilePath(filePath)
                || courseMaterialRepository.existsByFileUrl(filePath)
                || accreditationRecordRepository.existsByEvidencePath(filePath);
    }

    /**
     * Check if file exists
     * @param filePath relative file path
     * @return true if file exists
     */
    @GetMapping("/exists")
    @PreAuthorize("hasAuthority('ADMIN') or @fileAccessService.isOwnFile(#filePath)")
    public ResponseEntity<Boolean> fileExists(@RequestParam String filePath) {
        boolean exists = fileUploadService.fileExists(filePath);
        return ResponseEntity.ok(exists);
    }

    /**
     * Get file information
     * @param filePath relative file path
     * @return file size in bytes
     */
    @GetMapping("/info")
    @PreAuthorize("hasAuthority('ADMIN') or @fileAccessService.isOwnFile(#filePath)")
    public ResponseEntity<Long> getFileInfo(@RequestParam String filePath) {
        long fileSize = fileUploadService.getFileSize(filePath);
        return ResponseEntity.ok(fileSize);
    }

    /**
     * Determine content type based on file extension
     * @param filename the filename
     * @return content type
     */
    private String getContentType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (lower.endsWith(".png")) {
            return "image/png";
        } else if (lower.endsWith(".gif")) {
            return "image/gif";
        } else if (lower.endsWith(".pdf")) {
            return "application/pdf";
        }
        return "application/octet-stream";
    }
}
