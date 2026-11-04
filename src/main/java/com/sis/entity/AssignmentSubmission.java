package com.sis.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "assignment_submissions", uniqueConstraints = @UniqueConstraint(columnNames = {"assignment_id", "student_id"}))
public class AssignmentSubmission {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "assignment_id", nullable = false) private Long assignmentId;
    @Column(name = "student_id", nullable = false) private Long studentId;
    @Column(length = 2000) private String content;
    @Column(nullable = false) private LocalDateTime submittedAt;
    public AssignmentSubmission() {}
    public Long getId() { return id; }
    public Long getAssignmentId() { return assignmentId; }
    public void setAssignmentId(Long value) { assignmentId = value; }
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long value) { studentId = value; }
    public String getContent() { return content; }
    public void setContent(String value) { content = value; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime value) { submittedAt = value; }
}
