package com.sis.dto;

import java.time.LocalDateTime;

public class GradeCorrectionDto {
    private final Long id;
    private final Long gradeId;
    private final String status;
    private final String reason;
    private final String previousSnapshot;
    private final String authorizedBy;
    private final LocalDateTime authorizedAt;
    private final LocalDateTime submittedAt;
    private final LocalDateTime completedAt;

    public GradeCorrectionDto(Long id, Long gradeId, String status, String reason,
                              String previousSnapshot, String authorizedBy, LocalDateTime authorizedAt,
                              LocalDateTime submittedAt, LocalDateTime completedAt) {
        this.id = id;
        this.gradeId = gradeId;
        this.status = status;
        this.reason = reason;
        this.previousSnapshot = previousSnapshot;
        this.authorizedBy = authorizedBy;
        this.authorizedAt = authorizedAt;
        this.submittedAt = submittedAt;
        this.completedAt = completedAt;
    }

    public Long getId() { return id; }
    public Long getGradeId() { return gradeId; }
    public String getStatus() { return status; }
    public String getReason() { return reason; }
    public String getPreviousSnapshot() { return previousSnapshot; }
    public String getAuthorizedBy() { return authorizedBy; }
    public LocalDateTime getAuthorizedAt() { return authorizedAt; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
}
