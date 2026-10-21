package com.sis.dto;

import java.time.LocalDateTime;

public class StudentDocumentDto {
    private Long id;
    private String name;
    private String contentType;
    private LocalDateTime uploadedAt;
    private String status;

    public StudentDocumentDto() {}

    public StudentDocumentDto(Long id, String name, String contentType, LocalDateTime uploadedAt, String status) {
        this.id = id;
        this.name = name;
        this.contentType = contentType;
        this.uploadedAt = uploadedAt;
        this.status = status;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getContentType() { return contentType; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public String getStatus() { return status; }
}
