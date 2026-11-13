package com.sis.controller;

import com.sis.dto.AnnouncementDto;
import com.sis.dto.CreateAnnouncementRequest;
import com.sis.entity.User;
import com.sis.exception.ResourceNotFoundException;
import com.sis.service.SchoolService;
import com.sis.repository.UserRepository;
import com.sis.service.NotificationService;
import com.sis.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/announcements")
public class AnnouncementController {

    private final SchoolService schoolService;
    private final NotificationService notifications;
    private final UserRepository users;
    @Autowired
    private EmailService emailService;

    public AnnouncementController(SchoolService schoolService, NotificationService notifications, UserRepository users) {
        this.schoolService = schoolService;
        this.notifications = notifications;
        this.users = users;
    }

    @GetMapping
    public List<AnnouncementDto> listAnnouncements() {
        return schoolService.listAnnouncements();
    }

    @PostMapping
    @PreAuthorize("@permissionSecurity.canManageAnnouncements()")
    public ResponseEntity<AnnouncementDto> createAnnouncement(@Valid @RequestBody CreateAnnouncementRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User author = schoolService.getUserByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
        AnnouncementDto ann = schoolService.createAnnouncement(request.getTitle(), request.getContent(), author);
        users.findActiveUserIdsExcluding(author.getId())
                .forEach(userId -> notifications.create(userId, "New announcement", ann.getTitle()));
        users.findActiveUserIdsExcluding(author.getId()).forEach(userId ->
                users.findById(userId).ifPresent(user -> {
                    if (user.getEmail() == null || emailService == null) {
                        return;
                    }
                    try {
                        emailService.sendAnnouncementNotification(
                                user.getEmail(), user.getUsername(), ann.getTitle(), ann.getContent());
                    } catch (IllegalArgumentException | IllegalStateException ex) {
                        org.slf4j.LoggerFactory.getLogger(AnnouncementController.class)
                                .warn("Announcement email was not delivered for user {}: {}",
                                        userId, ex.getMessage());
                    }
                }));
        return ResponseEntity.status(HttpStatus.CREATED).body(ann);
    }
}
