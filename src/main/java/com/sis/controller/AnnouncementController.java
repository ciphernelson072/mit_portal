package com.sis.controller;

import com.sis.dto.AnnouncementDto;
import com.sis.dto.CreateAnnouncementRequest;
import com.sis.entity.User;
import com.sis.entity.NotificationCategory;
import com.sis.exception.ResourceNotFoundException;
import com.sis.service.SchoolService;
import com.sis.repository.UserRepository;
import com.sis.service.NotificationService;
import com.sis.service.EmailService;
import com.sis.service.ProfileContactEmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import com.sis.entity.AnnouncementAudience;
import com.sis.entity.Role;

@RestController
@RequestMapping("/api/announcements")
public class AnnouncementController {

    private final SchoolService schoolService;
    private final NotificationService notifications;
    private final UserRepository users;
    private final ProfileContactEmailService contactEmails;
    @Autowired
    private EmailService emailService;

    public AnnouncementController(SchoolService schoolService, NotificationService notifications, UserRepository users,
                                  ProfileContactEmailService contactEmails) {
        this.schoolService = schoolService;
        this.notifications = notifications;
        this.users = users;
        this.contactEmails = contactEmails;
    }

    @GetMapping
    public List<AnnouncementDto> listAnnouncements() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Role currentRole = schoolService.getUserByUsername(username)
                .map(User::getRole)
                .orElse(null);
        return schoolService.listAnnouncements().stream()
                .filter(announcement -> isVisibleTo(announcement.getAudience(), currentRole))
                .toList();
    }

    @PostMapping
    @PreAuthorize("@permissionSecurity.canManageAnnouncements()")
    public ResponseEntity<AnnouncementDto> createAnnouncement(@Valid @RequestBody CreateAnnouncementRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User author = schoolService.getUserByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
        AnnouncementDto ann = schoolService.createAnnouncement(
                request.getTitle(), request.getContent(), author, request.getAudience());
        List<User> recipients = users.findActiveUserIdsExcluding(author.getId()).stream()
                .map(users::findById)
                .flatMap(java.util.Optional::stream)
                .filter(user -> user.getRole() != null && isVisibleToAudience(
                        request.getAudience(), user.getRole()))
                .toList();
        recipients.forEach(user -> notifications.create(
                user.getId(), "New announcement", ann.getTitle(), NotificationCategory.ANNOUNCEMENT));
        var profileEmails = contactEmails.findForUsers(
                recipients.stream().map(User::getId).toList());
        recipients.forEach(user -> {
            Long userId = user.getId();
            if (userId == null || emailService == null) {
                return;
            }
            String email = user.getRole() == Role.ADMIN
                    ? user.getEmail()
                    : profileEmails.get(userId);
            if (email == null || !notifications.emailNotificationsEnabled(userId)) {
                return;
            }
            try {
                emailService.sendAnnouncementNotification(
                        email, user.getUsername(), ann.getTitle(), ann.getContent());
            } catch (IllegalArgumentException | IllegalStateException ex) {
                org.slf4j.LoggerFactory.getLogger(AnnouncementController.class)
                        .warn("Announcement email was not delivered for user {}: {}",
                                userId, ex.getMessage());
            }
        });
        return ResponseEntity.status(HttpStatus.CREATED).body(ann);
    }

    private boolean isVisibleTo(String audience, Role role) {
        if (audience == null) return true;
        try {
            return isVisibleToAudience(AnnouncementAudience.valueOf(audience), role);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean isVisibleToAudience(AnnouncementAudience audience, Role role) {
        return role != null && (audience == AnnouncementAudience.ALL
                || ("ADMIN".equals(audience.name()) && role == Role.ADMIN)
                || ("TEACHER".equals(audience.name()) && role == Role.TEACHER)
                || ("STUDENT".equals(audience.name()) && role == Role.STUDENT));
    }
}
