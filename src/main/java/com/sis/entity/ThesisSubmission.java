package com.sis.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "thesis_submissions")
public class ThesisSubmission {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "research_project_id", nullable = false) private Long researchProjectId;
    @Column(name = "file_path", nullable = false, length = 500) private String filePath;
    @Column(length = 2000) private String notes;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private ThesisSubmissionStatus status = ThesisSubmissionStatus.SUBMITTED;
    @Column(name = "reviewed_by") private String reviewedBy;
    @Column(name = "reviewed_at") private LocalDateTime reviewedAt;
    @Column(name = "submitted_at", nullable = false) private LocalDateTime submittedAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getResearchProjectId() { return researchProjectId; }
    public void setResearchProjectId(Long researchProjectId) { this.researchProjectId = researchProjectId; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public ThesisSubmissionStatus getStatus() { return status; }
    public void setStatus(ThesisSubmissionStatus status) { this.status = status; }
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
}