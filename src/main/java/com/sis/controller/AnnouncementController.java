package com.sis.controller;

import com.sis.dto.AnnouncementDto;
import com.sis.dto.CreateAnnouncementRequest;
import com.sis.entity.Announcement;
import com.sis.entity.User;
import com.sis.service.SchoolService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/announcements")
public class AnnouncementController {

    private final SchoolService schoolService;

    public AnnouncementController(SchoolService schoolService) {
        this.schoolService = schoolService;
    }

    @GetMapping
    public List<AnnouncementDto> listAnnouncements() {
        return schoolService.listAnnouncements();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('TEACHER')")
    public ResponseEntity<Announcement> createAnnouncement(@RequestBody CreateAnnouncementRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User author = schoolService.getUserByUsername(username).orElseThrow(() -> new RuntimeException("User not found"));
        Announcement ann = schoolService.createAnnouncement(request.getTitle(), request.getContent(), author);
        return ResponseEntity.ok(ann);
    }
}