package com.sis.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(name = "admission_application_documents", uniqueConstraints = @UniqueConstraint(
        name = "uk_admission_application_requirement", columnNames = {"application_id", "requirement_id"}))
public class AdmissionApplicationDocument {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false)
    private AdmissionApplication application;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "requirement_id", nullable = false)
    private AdmissionDocumentRequirement requirement;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt = LocalDateTime.now();

    public Long getId() { return id; }
    public AdmissionApplication getApplication() { return application; }
    public void setApplication(AdmissionApplication application) { this.application = application; }
    public AdmissionDocumentRequirement getRequirement() { return requirement; }
    public void setRequirement(AdmissionDocumentRequirement requirement) { this.requirement = requirement; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }
}
