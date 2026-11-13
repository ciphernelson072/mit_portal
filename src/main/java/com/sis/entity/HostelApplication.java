package com.sis.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "hostel_applications")
public class HostelApplication {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "student_id", nullable = false) private Long studentId;
    @Column(length = 2000) private String details;
    @Column(nullable = false) private String status = "OPEN";
    @Column(nullable = false) private LocalDateTime createdAt = LocalDateTime.now();
    public HostelApplication() {}
    public HostelApplication(Long studentId, String details) { this.studentId = studentId; this.details = details; }
    public Long getId() { return id; }
    public Long getStudentId() { return studentId; }
    public String getDetails() { return details; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
