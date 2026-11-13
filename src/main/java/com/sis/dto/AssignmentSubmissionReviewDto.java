package com.sis.dto;

import java.time.LocalDateTime;

public class AssignmentSubmissionReviewDto {
    private final Long id;
    private final Long studentId;
    private final String studentName;
    private final String content;
    private final LocalDateTime submittedAt;

    public AssignmentSubmissionReviewDto(Long id, Long studentId, String studentName,
                                         String content, LocalDateTime submittedAt) {
        this.id = id;
        this.studentId = studentId;
        this.studentName = studentName;
        this.content = content;
        this.submittedAt = submittedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getStudentId() {
        return studentId;
    }

    public String getStudentName() {
        return studentName;
    }

    public String getContent() {
        return content;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }
}
