package com.sis.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "student_documents", indexes = {
        @Index(name = "idx_student_document_student", columnList = "student_id")
})
public class StudentDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 500)
    private String filePath;

    @Column(nullable = false, length = 100)
    private String contentType;

    @Column(nullable = false)
    private LocalDateTime uploadedAt = LocalDateTime.now();

    @Column(nullable = false, length = 30)
    private String status = "PENDING";

    public StudentDocument() {}

    public StudentDocument(Student student, String name, String filePath, String contentType) {
        this.student = student;
        this.name = name;
        this.filePath = filePath;
        this.contentType = contentType;
    }

    public Long getId() { return id; }
    public Student getStudent() { return student; }
    public String getName() { return name; }
    public String getFilePath() { return filePath; }
    public String getContentType() { return contentType; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public String getStatus() { return status; }
}
