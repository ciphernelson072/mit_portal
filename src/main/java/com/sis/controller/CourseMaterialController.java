package com.sis.controller;

import com.sis.dto.CourseMaterialDto;
import com.sis.dto.CreateMaterialRequest;
import com.sis.entity.CourseMaterial;
import com.sis.entity.User;
import com.sis.service.SchoolService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/materials")
public class CourseMaterialController {

    private final SchoolService schoolService;

    public CourseMaterialController(SchoolService schoolService) {
        this.schoolService = schoolService;
    }

    @GetMapping("/course/{courseId}")
    public ResponseEntity<List<CourseMaterialDto>> getMaterials(@PathVariable Long courseId) {
        return ResponseEntity.ok(schoolService.getMaterialsForCourse(courseId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('TEACHER')")
    public ResponseEntity<CourseMaterial> createMaterial(@RequestBody CreateMaterialRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User uploader = schoolService.getUserByUsername(username).orElseThrow(() -> new RuntimeException("User not found"));
        return schoolService.getCourseById(request.getCourseId())
                .map(course -> ResponseEntity.ok(schoolService.createMaterial(course, request.getTitle(), request.getDescription(), request.getFileUrl(), uploader)))
                .orElse(ResponseEntity.notFound().build());
    }
}