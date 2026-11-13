package com.sis.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "research_projects")
public class ResearchProject {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "student_id", nullable = false) private Long studentId;
    @Column(nullable = false, length = 255) private String title;
    @Column(length = 2000) private String abstractText;
    @Column(name = "supervisor_user_id") private Long supervisorUserId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private ResearchProjectStatus status = ResearchProjectStatus.PROPOSED;
    @Column(name = "reviewed_by") private String reviewedBy;
    @Column(name = "reviewed_at") private LocalDateTime reviewedAt;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getAbstractText() { return abstractText; }
    public void setAbstractText(String abstractText) { this.abstractText = abstractText; }
    public Long getSupervisorUserId() { return supervisorUserId; }
    public void setSupervisorUserId(Long supervisorUserId) { this.supervisorUserId = supervisorUserId; }
    public ResearchProjectStatus getStatus() { return status; }
    public void setStatus(ResearchProjectStatus status) { this.status = status; }
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}