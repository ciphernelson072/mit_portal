package com.sis.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "service_requests", indexes = {
        @Index(name = "idx_service_request_student", columnList = "student_id"),
        @Index(name = "idx_service_request_status", columnList = "status")
})
public class ServiceRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id")
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_id")
    private User requester;

    @Column(nullable = false, length = 40)
    private String type;

    @Column(nullable = false, length = 255)
    private String subject;

    @Column(length = 2000)
    private String details;

    @Column(nullable = false, length = 30)
    private String status = "OPEN";

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(length = 50)
    private String requesterRole = "STUDENT";

    public ServiceRequest() {}

    public ServiceRequest(Student student, User requester, String type, String subject, String details) {
        this.student = student;
        this.requester = requester;
        this.type = type;
        this.subject = subject;
        this.details = details;
        this.requesterRole = requester == null ? "STUDENT" : requester.getRole().name();
    }

    public Long getId() { return id; }
    public Student getStudent() { return student; }
    public User getRequester() { return requester; }
    public String getType() { return type; }
    public String getSubject() { return subject; }
    public String getDetails() { return details; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public String getRequesterRole() { return requesterRole; }
    public void setStatus(String status) { this.status = status; }
    public void setRequesterRole(String requesterRole) { this.requesterRole = requesterRole; }
}
