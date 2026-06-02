package com.sis.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "course_materials")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class CourseMaterial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "course_id")
    private Course course;

    @Column(nullable = false)
    private String title;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false)
    private String fileUrl;

    @Column(nullable = false)
    private LocalDateTime uploadedAt;

    @ManyToOne
    @JoinColumn(name = "uploaded_by")
    private User uploadedBy;

    public CourseMaterial() {
    }

    public CourseMaterial(Course course, String title, String description, String fileUrl, User uploadedBy) {
        this.course = course;
        this.title = title;
        this.description = description;
        this.fileUrl = fileUrl;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Course getCourse() {
        return course;
    }

    public void setCourse(Course course) {
        this.course = course;
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

    public User getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(User uploadedBy) {
        this.uploadedBy = uploadedBy;
    }
}