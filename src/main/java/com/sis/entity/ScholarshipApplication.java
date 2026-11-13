package com.sis.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "scholarship_applications", uniqueConstraints = @UniqueConstraint(columnNames = {"scholarship_id", "student_id"}))
public class ScholarshipApplication {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "scholarship_id", nullable = false) private Long scholarshipId;
    @Column(name = "student_id", nullable = false) private Long studentId;
    @Column(nullable = false) private String status = "SUBMITTED";
    @Column(nullable = false) private LocalDateTime appliedAt = LocalDateTime.now();
    public ScholarshipApplication() {}
    public ScholarshipApplication(Long scholarshipId, Long studentId) { this.scholarshipId = scholarshipId; this.studentId = studentId; }
    public Long getId() { return id; }
    public Long getScholarshipId() { return scholarshipId; }
    public Long getStudentId() { return studentId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getAppliedAt() { return appliedAt; }
}
