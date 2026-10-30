package com.sis.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "grade_correction_requests",
        indexes = @Index(name = "idx_grade_correction_grade_status", columnList = "grade_id, status"))
public class GradeCorrectionRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "row_version", nullable = false)
    private Long rowVersion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grade_id", nullable = false)
    private Grade grade;

    @Column(nullable = false, length = 2000)
    private String reason;

    @Lob
    @Column(name = "previous_snapshot", nullable = false)
    private String previousSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GradeCorrectionStatus status = GradeCorrectionStatus.AUTHORIZED;

    @Column(name = "authorized_by", nullable = false, length = 255)
    private String authorizedBy;

    @Column(name = "authorized_at", nullable = false)
    private LocalDateTime authorizedAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    protected GradeCorrectionRequest() {
    }

    public GradeCorrectionRequest(Grade grade, String reason, String previousSnapshot,
                                  String authorizedBy, LocalDateTime authorizedAt) {
        this.grade = grade;
        this.reason = reason;
        this.previousSnapshot = previousSnapshot;
        this.authorizedBy = authorizedBy;
        this.authorizedAt = authorizedAt;
    }

    public Long getId() { return id; }
    public Grade getGrade() { return grade; }
    public String getReason() { return reason; }
    public String getPreviousSnapshot() { return previousSnapshot; }
    public GradeCorrectionStatus getStatus() { return status; }
    public void setStatus(GradeCorrectionStatus status) { this.status = status; }
    public String getAuthorizedBy() { return authorizedBy; }
    public LocalDateTime getAuthorizedAt() { return authorizedAt; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
}
