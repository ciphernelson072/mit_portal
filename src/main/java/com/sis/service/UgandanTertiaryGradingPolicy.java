package com.sis.service;

import com.sis.entity.ProgramAwardType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

public final class UgandanTertiaryGradingPolicy {

    public static final String VERSION = "UG_5_POINT_V1";
    public static final BigDecimal PASS_MARK = new BigDecimal("50");
    public static final BigDecimal PASS_GRADE_POINT = new BigDecimal("2.0");
    public static final BigDecimal MAX_GRADE_POINT = new BigDecimal("5.0");

    private static final List<GradeBand> GRADE_BANDS = List.of(
            new GradeBand(new BigDecimal("80"), "A", new BigDecimal("5.0"), "Exceptional / Distinction"),
            new GradeBand(new BigDecimal("75"), "B+", new BigDecimal("4.5"), "Excellent"),
            new GradeBand(new BigDecimal("70"), "B", new BigDecimal("4.0"), "Very Good"),
            new GradeBand(new BigDecimal("65"), "C+", new BigDecimal("3.5"), "Good"),
            new GradeBand(new BigDecimal("60"), "C", new BigDecimal("3.0"), "Fair"),
            new GradeBand(new BigDecimal("55"), "D+", new BigDecimal("2.5"), "Satisfactory"),
            new GradeBand(new BigDecimal("50"), "D", new BigDecimal("2.0"), "Pass"),
            new GradeBand(BigDecimal.ZERO, "F", BigDecimal.ZERO, "Fail / Retake Required"));

    private UgandanTertiaryGradingPolicy() {
    }

    public static GradeBand gradeForMark(BigDecimal mark) {
        if (mark == null || mark.signum() < 0 || mark.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("Percentage mark must be between 0 and 100.");
        }
        return GRADE_BANDS.stream()
                .filter(band -> mark.compareTo(band.minimumMark()) >= 0)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No grade band is configured for the percentage mark."));
    }

    public static BigDecimal gradePoint(String gradeValue) {
        if (gradeValue == null) throw new IllegalArgumentException("Grade value is required.");
        String normalized = gradeValue.trim().toUpperCase(Locale.ROOT);
        return GRADE_BANDS.stream()
                .filter(band -> band.letterGrade().equals(normalized))
                .map(GradeBand::gradePoint)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unrecognized grade for the " + VERSION + " policy: " + gradeValue));
    }

    public static boolean isPassingMark(BigDecimal mark) {
        return gradeForMark(mark).gradePoint().compareTo(PASS_GRADE_POINT) >= 0;
    }

    public static boolean requiresRetake(BigDecimal mark) {
        return gradeForMark(mark).gradePoint().compareTo(PASS_GRADE_POINT) < 0;
    }

    public static String awardClassification(BigDecimal cgpa, ProgramAwardType awardType) {
        if (cgpa == null || awardType == null) return null;
        if (cgpa.signum() < 0 || cgpa.compareTo(MAX_GRADE_POINT) > 0) {
            throw new IllegalArgumentException("CGPA must be between 0.00 and 5.00.");
        }
        if (cgpa.compareTo(new BigDecimal("4.40")) >= 0) {
            return awardType == ProgramAwardType.BACHELOR
                    ? "First Class (Honours)" : "Distinction / Class I";
        }
        if (cgpa.compareTo(new BigDecimal("3.60")) >= 0) {
            return awardType == ProgramAwardType.BACHELOR
                    ? "Second Class - Upper Division" : "Credit / Class II (Upper)";
        }
        if (cgpa.compareTo(new BigDecimal("2.80")) >= 0) {
            return awardType == ProgramAwardType.BACHELOR
                    ? "Second Class - Lower Division" : "Credit / Class II (Lower)";
        }
        if (cgpa.compareTo(PASS_GRADE_POINT) >= 0) {
            return awardType == ProgramAwardType.BACHELOR ? "Pass (General Degree)" : "Pass / Class III";
        }
        return awardType == ProgramAwardType.BACHELOR ? "Fail / No Award" : "Fail";
    }

    public record GradeBand(BigDecimal minimumMark, String letterGrade, BigDecimal gradePoint, String description) {
    }
}
