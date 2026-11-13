package com.sis.controller;

import com.sis.dto.CourseMaterialDto;
import com.sis.entity.Role;
import com.sis.entity.User;
import com.sis.exception.ResourceNotFoundException;
import com.sis.service.SchoolService;
import com.sis.service.FileUploadService;
import com.sis.repository.CourseMaterialRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/materials")
public class CourseMaterialController {

    private final SchoolService schoolService;
    private final FileUploadService fileUploadService;
    private final CourseMaterialRepository materials;

    public CourseMaterialController(SchoolService schoolService, FileUploadService fileUploadService,
                                    CourseMaterialRepository materials) {
        this.schoolService = schoolService;
        this.fileUploadService = fileUploadService;
        this.materials = materials;
    }

    @GetMapping("/course/{courseId}")
    @PreAuthorize("hasAuthority('ADMIN') or @teacherAccessService.teachesCourse(#courseId) or @studentAccessService.isEnrolledInCourse(#courseId)")
    public ResponseEntity<List<CourseMaterialDto>> getMaterials(@PathVariable Long courseId) {
        return ResponseEntity.ok(schoolService.getMaterialsForCourse(courseId));
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> downloadMaterial(@PathVariable Long id) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return materials.findById(id).map(material -> {
            boolean allowed = isAuthorizedForMaterial(username, material);
            if (!allowed) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).<byte[]>build();
            }
            try {
                String filePath = material.getFileUrl();
                byte[] bytes = fileUploadService.getFileAsBytes(filePath);
                String filename = filePath.substring(filePath.lastIndexOf('/') + 1);
                return ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                        .body(bytes);
            } catch (IOException e) {
                return ResponseEntity.notFound().<byte[]>build();
            }
        }).orElse(ResponseEntity.notFound().build());
    }

    private boolean isAuthorizedForMaterial(String username, com.sis.entity.CourseMaterial material) {
        if (material == null || material.getCourse() == null) {
            return false;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }

        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ADMIN".equals(authority.getAuthority()));
        if (admin) {
            return true;
        }

        if (schoolService.teacherOwnsCourse(username, material.getCourse())) {
            return true;
        }

        return schoolService.getStudentByUsername(username)
                .map(student -> schoolService.studentEnrolledInCourse(student.getId(), material.getCourse().getId()))
                .orElse(false);
    }

    @PostMapping
    @PreAuthorize("@permissionSecurity.canManageCourseMaterials()")
    public ResponseEntity<CourseMaterialDto> createMaterial(
            @RequestParam Long courseId,
            @RequestParam String title,
            @RequestParam(required = false, defaultValue = "") String description,
            @RequestPart("file") MultipartFile file) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User uploader = schoolService.getUserByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
        var course = schoolService.getCourseById(courseId)
            .orElseThrow(() -> new ResourceNotFoundException("Course not found with id: " + courseId));
        boolean administrator = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(authority -> "ADMIN".equals(authority.getAuthority()));
        boolean teacher = uploader.getRole() == Role.TEACHER
                && schoolService.teacherOwnsCourse(username, course);
        if (!administrator && !teacher) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        try {
            String filePath = fileUploadService.uploadCourseMaterial(file, courseId);
            CourseMaterialDto created = schoolService.createMaterial(course, title, description, filePath, uploader);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IOException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
