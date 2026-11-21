package com.sis.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class AnnouncementDto {
    private Long id;
    private String title;
    private String content;
    private LocalDateTime createdAt;
    private String authorName;
    private String audience;
    private int inAppRecipientCount;
    private int emailRecipientCount;
    private int emailSuccessCount;
    private int emailFailureCount;
    private int emailSkippedCount;
    private List<Long> programIds = new ArrayList<>();
    private List<Long> courseIds = new ArrayList<>();

    public AnnouncementDto() {
    }

    public AnnouncementDto(Long id, String title, String content, LocalDateTime createdAt, String authorName) {
        this(id, title, content, createdAt, authorName, "ALL");
    }

    public AnnouncementDto(Long id, String title, String content, LocalDateTime createdAt, String authorName,
                           String audience) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.createdAt = createdAt;
        this.authorName = authorName;
        this.audience = audience == null ? "ALL" : audience;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getAuthorName() {
        return authorName;
    }

    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }

    public int getInAppRecipientCount() { return inAppRecipientCount; }
    public void setInAppRecipientCount(int inAppRecipientCount) { this.inAppRecipientCount = inAppRecipientCount; }
    public int getEmailRecipientCount() { return emailRecipientCount; }
    public void setEmailRecipientCount(int emailRecipientCount) { this.emailRecipientCount = emailRecipientCount; }
    public int getEmailSuccessCount() { return emailSuccessCount; }
    public void setEmailSuccessCount(int emailSuccessCount) { this.emailSuccessCount = emailSuccessCount; }
    public int getEmailFailureCount() { return emailFailureCount; }
    public void setEmailFailureCount(int emailFailureCount) { this.emailFailureCount = emailFailureCount; }
    public int getEmailSkippedCount() { return emailSkippedCount; }
    public void setEmailSkippedCount(int emailSkippedCount) { this.emailSkippedCount = emailSkippedCount; }
    public List<Long> getProgramIds() { return programIds; }
    public void setProgramIds(List<Long> programIds) { this.programIds = programIds == null ? new ArrayList<>() : programIds; }
    public List<Long> getCourseIds() { return courseIds; }
    public void setCourseIds(List<Long> courseIds) { this.courseIds = courseIds == null ? new ArrayList<>() : courseIds; }
}