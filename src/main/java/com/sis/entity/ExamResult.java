package com.sis.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "exam_results", uniqueConstraints = @UniqueConstraint(columnNames = {"exam_id", "student_id"}))
public class ExamResult {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "exam_id", nullable = false) private Long examId;
    @Column(name = "student_id", nullable = false) private Long studentId;
    @Column(precision = 6, scale = 2, nullable = false) private BigDecimal score;
    @Column(name = "max_score", precision = 6, scale = 2, nullable = false) private BigDecimal maxScore = BigDecimal.valueOf(100);
    @Column(length = 1000) private String remarks;
    @Column(nullable = false) private Boolean published = false;
    @Column(name = "published_at") private LocalDateTime publishedAt;
    public ExamResult() {}
    public Long getId() { return id; }
    public Long getExamId() { return examId; }
    public void setExamId(Long value) { examId = value; }
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long value) { studentId = value; }
    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal value) { score = value; }
    public BigDecimal getMaxScore() { return maxScore; }
    public void setMaxScore(BigDecimal value) { maxScore = value; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String value) { remarks = value; }
    public Boolean getPublished() { return published; }
    public void setPublished(Boolean value) { published = value; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime value) { publishedAt = value; }
}
