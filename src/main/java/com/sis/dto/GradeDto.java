package com.sis.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Read-only representation of a Grade returned to API clients.
 * Never expose the {@code Grade} entity directly - it carries a JPA-managed
 * Student/Course graph that would either lazy-load explode or leak far more
 * than the client needs.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GradeDto {
    private Long id;
    private Long courseId;
    private String courseCode;
    private String courseName;
    private String gradeValue;
    private BigDecimal gpaScore;
    private String remarks;
    private Integer credits;
    private String semester;
    private String status;
    private String submittedBy;
    private String approvedBy;
    private String rejectionReason;
    private Integer attemptNumber;
    private Long originalAttemptId;
    private String attemptType;
    private BigDecimal totalMark;
    private Boolean retakeRequired;
    private String gradingPolicyVersion;
    private List<AssessmentMarkDto> assessmentComponents;
}
