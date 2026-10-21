package com.sis.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
public class Notification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(nullable = false) private String title;
    @Column(length = 2000, nullable = false) private String message;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationCategory category = NotificationCategory.SYSTEM;
    @Column(name = "read_status", nullable = false) private boolean read = false;
    @Column(nullable = false) private LocalDateTime createdAt = LocalDateTime.now();
    public Notification() {}
    public Notification(Long userId, String title, String message) {
        this(userId, title, message, NotificationCategory.SYSTEM);
    }
    public Notification(Long userId, String title, String message, NotificationCategory category) {
        this.userId = userId;
        this.title = title;
        this.message = message;
        this.category = category;
    }
    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public NotificationCategory getCategory() { return category; }
    public boolean isRead() { return read; }
    public void setRead(boolean value) { read = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
