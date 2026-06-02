package com.sis.dto;

import java.time.LocalDateTime;

public class AnnouncementDto {
    private Long id;
    private String title;
    private String content;
    private LocalDateTime createdAt;
    private String authorName;

    public AnnouncementDto() {
    }

    public AnnouncementDto(Long id, String title, String content, LocalDateTime createdAt, String authorName) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.createdAt = createdAt;
        this.authorName = authorName;
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
}