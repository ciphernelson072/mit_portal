package com.sis.controller;

import com.sis.dto.AdminCourseDto;
import com.sis.service.SchoolService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/public")
public class PublicController {

    private final SchoolService schoolService;

    public PublicController(SchoolService schoolService) {
        this.schoolService = schoolService;
    }

    @GetMapping("/announcements")
    public List<Map<String, Object>> getPublicAnnouncements() {
        return schoolService.getPublicAnnouncements();
    }

    @GetMapping("/courses")
    public List<AdminCourseDto> getPublicCourses() {
        return schoolService.listCourseDtos();
    }

    @GetMapping("/stats")
    public Map<String, Long> getPublicStats() {
        return schoolService.getPublicStats();
    }
}