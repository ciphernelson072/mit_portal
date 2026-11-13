package com.sis.controller;

import com.sis.entity.Notification;
import com.sis.service.NotificationService;
import com.sis.service.SchoolService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notifications;
    private final SchoolService schoolService;

    public NotificationController(NotificationService notifications, SchoolService schoolService) {
        this.notifications = notifications;
        this.schoolService = schoolService;
    }

    @GetMapping
    public ResponseEntity<List<Notification>> list() {
        return currentUserId()
                .map(id -> ResponseEntity.ok(notifications.listVisibleForUser(id)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable Long id) {
        return currentUserId()
                .map(userId -> { notifications.markRead(userId, id); return ResponseEntity.ok().<Void>build(); })
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllRead() {
        currentUserId().ifPresent(notifications::markAllRead);
        return ResponseEntity.ok().build();
    }

    private java.util.Optional<Long> currentUserId() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return schoolService.getUserByUsername(username).map(user -> user.getId());
    }
}