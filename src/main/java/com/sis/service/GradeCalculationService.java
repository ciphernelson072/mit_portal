package com.sis.service;

import com.sis.entity.Course;
import com.sis.entity.Grade;
import com.sis.entity.GradeStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Single source of truth for grade-to-GPA conversion and approved-grade CGPA. */
public final class GradeCalculationService {

    private GradeCalculationService() {
    }

    public static BigDecimal gradeToGpa(String gradeValue) {
        if (gradeValue == null) return BigDecimal.ZERO;
        return switch (gradeValue.trim().toUpperCase()) {
            case "A+", "A" -> new BigDecimal("4.0");
            case "A-" -> new BigDecimal("3.7");
            case "B+" -> new BigDecimal("3.3");
            case "B" -> new BigDecimal("3.0");
            case "B-" -> new BigDecimal("2.7");
            case "C+" -> new BigDecimal("2.3");
            case "C" -> new BigDecimal("2.0");
            case "C-" -> new BigDecimal("1.7");
            case "D+" -> new BigDecimal("1.3");
            case "D" -> new BigDecimal("1.0");
            case "D-" -> new BigDecimal("0.7");
            default -> BigDecimal.ZERO;
        };
    }

    public static BigDecimal calculateApprovedCgpa(List<Grade> grades) {
        BigDecimal weightedTotal = BigDecimal.ZERO;
        int totalCredits = 0;
        for (Grade grade : grades) {
            if (grade == null || grade.getStatus() != GradeStatus.APPROVED) continue;
            Course course = grade.getCourse();
            int credits = course != null && course.getCredits() != null ? course.getCredits() : 3;
            BigDecimal gpa = grade.getGpaScore() != null ? grade.getGpaScore() : BigDecimal.ZERO;
            weightedTotal = weightedTotal.add(gpa.multiply(BigDecimal.valueOf(credits)));
            totalCredits += credits;
        }
        return totalCredits == 0
                ? BigDecimal.ZERO
                : weightedTotal.divide(BigDecimal.valueOf(totalCredits), 2, RoundingMode.HALF_UP);
    }
}