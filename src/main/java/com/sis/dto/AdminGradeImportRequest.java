package com.sis.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

public class AdminGradeImportRequest {
    @NotNull private Long studentId;
    @NotNull private Long courseId;
    @NotBlank private String gradeValue;
    private BigDecimal gpaScore;
    private String remarks;
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long value) { studentId = value; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long value) { courseId = value; }
    public String getGradeValue() { return gradeValue; }
    public void setGradeValue(String value) { gradeValue = value; }
    public BigDecimal getGpaScore() { return gpaScore; }
    public void setGpaScore(BigDecimal value) { gpaScore = value; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String value) { remarks = value; }
}