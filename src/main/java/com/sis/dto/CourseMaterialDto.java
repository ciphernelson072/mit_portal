package com.sis.dto;

import java.time.LocalDateTime;

public class CourseMaterialDto {
    private Long id;
    private String title;
    private String description;
    private String fileUrl;
    private LocalDateTime uploadedAt;
    private String uploadedBy;

    public CourseMaterialDto() {
    }

    public CourseMaterialDto(Long id, String title, String description, String fileUrl, LocalDateTime uploadedAt, String uploadedBy) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.fileUrl = fileUrl;
        this.uploadedAt = uploadedAt;
        this.uploadedBy = uploadedBy;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getFileUrl() {
        return fileUrl;
    }

    public void setFileUrl(String fileUrl) {
        this.fileUrl = fileUrl;
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(LocalDateTime uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public String getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(String uploadedBy) {
        this.uploadedBy = uploadedBy;
    }
}