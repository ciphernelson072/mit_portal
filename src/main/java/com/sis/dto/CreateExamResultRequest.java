package com.sis.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public class CreateExamResultRequest {
    @NotNull
    private Long examId;

    @NotNull
    private Long studentId;

    @NotNull
    @DecimalMin(value = "0.0")
    private BigDecimal score;

    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal maxScore;

    @Size(max = 1000)
    private String remarks;

    public Long getExamId() { return examId; }
    public void setExamId(Long examId) { this.examId = examId; }
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }
    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal score) { this.score = score; }
    public BigDecimal getMaxScore() { return maxScore; }
    public void setMaxScore(BigDecimal maxScore) { this.maxScore = maxScore; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
}
