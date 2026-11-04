package com.sis.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
public class AuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id") private Long userId;
    @Column(nullable = false) private String action;
    @Column(length = 2000) private String details;
    @Column(nullable = false) private LocalDateTime createdAt = LocalDateTime.now();
    public AuditLog() {}
    public AuditLog(Long userId, String action, String details) { this.userId = userId; this.action = action; this.details = details; }
    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getAction() { return action; }
    public String getDetails() { return details; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
