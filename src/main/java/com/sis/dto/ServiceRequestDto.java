package com.sis.dto;

import java.time.LocalDateTime;

public class ServiceRequestDto {
    private Long id;
    private String type;
    private String subject;
    private String details;
    private String status;
    private LocalDateTime createdAt;
    private String studentUsername;
    private String studentName;
    private String requesterUsername;
    private String requesterName;
    private String requesterRole;
    private LocalDateTime updatedAt;
    private Long assigneeId;
    private String assigneeUsername;
    private LocalDateTime assignedAt;

    public ServiceRequestDto() {}

    public ServiceRequestDto(Long id, String type, String subject, String details, String status, LocalDateTime createdAt) {
        this.id = id;
        this.type = type;
        this.subject = subject;
        this.details = details;
        this.status = status;
        this.createdAt = createdAt;
    }

    public ServiceRequestDto(Long id, String type, String subject, String details, String status,
                             LocalDateTime createdAt, String studentUsername, String studentName) {
        this(id, type, subject, details, status, createdAt);
        this.studentUsername = studentUsername;
        this.studentName = studentName;
        this.requesterUsername = studentUsername;
        this.requesterName = studentName;
    }

    public ServiceRequestDto(Long id, String type, String subject, String details, String status,
                             LocalDateTime createdAt, String studentUsername, String studentName,
                             String requesterRole) {
        this(id, type, subject, details, status, createdAt, studentUsername, studentName);
        this.requesterRole = requesterRole;
    }

    public Long getId() { return id; }
    public String getType() { return type; }
    public String getSubject() { return subject; }
    public String getDetails() { return details; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public String getStudentUsername() { return studentUsername; }
    public String getStudentName() { return studentName; }
    public String getRequesterUsername() { return requesterUsername; }
    public String getRequesterName() { return requesterName; }
    public String getRequesterRole() { return requesterRole; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public Long getAssigneeId() { return assigneeId; }
    public String getAssigneeUsername() { return assigneeUsername; }
    public LocalDateTime getAssignedAt() { return assignedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public void setAssigneeId(Long assigneeId) { this.assigneeId = assigneeId; }
    public void setAssigneeUsername(String assigneeUsername) { this.assigneeUsername = assigneeUsername; }
    public void setAssignedAt(LocalDateTime assignedAt) { this.assignedAt = assignedAt; }
}
