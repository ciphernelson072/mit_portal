package com.sis.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "academic_result_snapshots",
        uniqueConstraints = @UniqueConstraint(name = "uk_academic_result_snapshot_version",
                columnNames = {"student_id", "results_version"}),
        indexes = @Index(name = "idx_academic_result_snapshot_student_published",
                columnList = "student_id, published_at"))
public class AcademicResultSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "results_version", nullable = false, columnDefinition = "CHAR(64)")
    private String resultsVersion;

    @Lob
    @Column(name = "snapshot_payload", nullable = false, columnDefinition = "LONGTEXT")
    private String snapshotPayload;

    @Column(name = "approved_by", nullable = false, length = 255)
    private String approvedBy;

    @Column(name = "approved_at", nullable = false)
    private LocalDateTime approvedAt;

    @Column(name = "published_by", length = 255)
    private String publishedBy;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Version
    @Column(name = "row_version", nullable = false)
    private Long rowVersion;

    @JsonIgnore
    @Transient
    private boolean publicationNotificationPending;

    protected AcademicResultSnapshot() {
    }

    public AcademicResultSnapshot(Long studentId, String resultsVersion, String snapshotPayload,
                                  String approvedBy, LocalDateTime approvedAt) {
        this.studentId = studentId;
        this.resultsVersion = resultsVersion;
        this.snapshotPayload = snapshotPayload;
        this.approvedBy = approvedBy;
        this.approvedAt = approvedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getStudentId() {
        return studentId;
    }

    public String getResultsVersion() {
        return resultsVersion;
    }

    public String getSnapshotPayload() {
        return snapshotPayload;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }

    public String getPublishedBy() {
        return publishedBy;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public void publish(String username, LocalDateTime publishedAt) {
        if (this.publishedAt == null) {
            this.publishedBy = username;
            this.publishedAt = publishedAt;
            this.publicationNotificationPending = true;
        }
    }

    @JsonIgnore
    public boolean isPublicationNotificationPending() {
        return publicationNotificationPending;
    }

    @JsonIgnore
    public void clearPublicationNotificationPending() {
        this.publicationNotificationPending = false;
    }
}
