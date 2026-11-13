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
        return UgandanTertiaryGradingPolicy.gradePoint(gradeValue);
    }

    public static boolean isPassingGrade(String gradeValue) {
        return gradeToGpa(gradeValue).compareTo(UgandanTertiaryGradingPolicy.PASS_GRADE_POINT) >= 0;
    }

    public static boolean requiresRetake(String gradeValue) {
        return gradeToGpa(gradeValue).compareTo(UgandanTertiaryGradingPolicy.PASS_GRADE_POINT) < 0;
    }

    public static String academicStanding(BigDecimal cgpa, List<Grade> grades, List<Long> registeredCoreCourseIds) {
        if (grades.stream().filter(grade -> grade != null && grade.getStatus() == GradeStatus.APPROVED)
                .anyMatch(grade -> !UgandanTertiaryGradingPolicy.VERSION.equals(grade.getGradingPolicyVersion()))) {
            return "NOT_FINALIZED";
        }
        if (cgpa != null && cgpa.compareTo(UgandanTertiaryGradingPolicy.PASS_GRADE_POINT) < 0) {
            return "PP";
        }
        if (registeredCoreCourseIds == null || registeredCoreCourseIds.isEmpty()) {
            return "NOT_FINALIZED";
        }
        java.util.Map<Long, Grade> approvedCoreGrades = new java.util.HashMap<>();
        for (Grade grade : grades) {
            if (grade == null || grade.getCourse() == null || grade.getCourse().getId() == null
                    || !registeredCoreCourseIds.contains(grade.getCourse().getId())) continue;
            if (grade.getStatus() == GradeStatus.APPROVED
                    && grade.getGpaScore() != null
                    && grade.getGpaScore().compareTo(UgandanTertiaryGradingPolicy.PASS_GRADE_POINT) < 0) {
                return "PP";
            }
            if (grade.getStatus() == GradeStatus.APPROVED) {
                approvedCoreGrades.put(grade.getCourse().getId(), grade);
            }
        }
        return approvedCoreGrades.size() == registeredCoreCourseIds.size() ? "NP" : "NOT_FINALIZED";
    }

    public static BigDecimal calculateApprovedCgpa(List<Grade> grades) {
        BigDecimal weightedTotal = BigDecimal.ZERO;
        int totalCredits = 0;
        for (Grade grade : grades) {
            if (grade == null || grade.getStatus() != GradeStatus.APPROVED) continue;
            if (grade.getOriginalAttempt() != null || (grade.getAttemptNumber() != null && grade.getAttemptNumber() > 1)) {
                throw new IllegalStateException(
                        "CGPA cannot be finalized for retake attempts until the approved institutional retake policy is configured.");
            }
            Course course = grade.getCourse();
            if (course == null || course.getCredits() == null || course.getCredits() <= 0) {
                throw new IllegalStateException("Approved grades require an approved positive course credit value.");
            }
            int credits = course.getCredits();
            if (!UgandanTertiaryGradingPolicy.VERSION.equals(grade.getGradingPolicyVersion())) {
                throw new IllegalStateException(
                        "CGPA is not finalized: an approved result uses an unverified legacy grading policy.");
            }
            BigDecimal expectedGpa = gradeToGpa(grade.getGradeValue());
            BigDecimal gpa = grade.getGpaScore();
            if (gpa == null || gpa.compareTo(expectedGpa) != 0
                    || gpa.signum() < 0 || gpa.compareTo(UgandanTertiaryGradingPolicy.MAX_GRADE_POINT) > 0) {
                throw new IllegalStateException("Approved grade points do not match the institutional grading policy.");
            }
            if (grade.getTotalMark() != null
                    && !gradeToGpa(UgandanTertiaryGradingPolicy.gradeForMark(grade.getTotalMark()).letterGrade())
                    .equals(expectedGpa)) {
                throw new IllegalStateException("Approved grade does not match its percentage mark.");
            }
            weightedTotal = weightedTotal.add(gpa.multiply(BigDecimal.valueOf(credits)));
            totalCredits += credits;
        }
        return totalCredits == 0
                ? BigDecimal.ZERO
                : weightedTotal.divide(BigDecimal.valueOf(totalCredits), 2, RoundingMode.HALF_UP);
    }

    public static int calculateCreditsEarned(List<Grade> grades) {
        int creditsEarned = 0;
        for (Grade grade : grades) {
            if (grade == null || grade.getStatus() != GradeStatus.APPROVED) continue;
            if (grade.getOriginalAttempt() != null
                    || (grade.getAttemptNumber() != null && grade.getAttemptNumber() > 1)) {
                throw new IllegalStateException(
                        "Credits earned cannot be finalized until the institutional retake policy is configured.");
            }
            if (!UgandanTertiaryGradingPolicy.VERSION.equals(grade.getGradingPolicyVersion())) {
                throw new IllegalStateException(
                        "Credits earned cannot be finalized for an unverified legacy grading policy.");
            }
            if (grade.getGpaScore() == null
                    || grade.getGpaScore().compareTo(gradeToGpa(grade.getGradeValue())) != 0) {
                throw new IllegalStateException("Approved grade points do not match the institutional grading policy.");
            }
            if (grade.getGpaScore().compareTo(UgandanTertiaryGradingPolicy.PASS_GRADE_POINT) >= 0) {
                if (grade.getCourse() == null || grade.getCourse().getCredits() == null
                        || grade.getCourse().getCredits() <= 0) {
                    throw new IllegalStateException("Passed courses require an approved positive credit value.");
                }
                creditsEarned += grade.getCourse().getCredits();
            }
        }
        return creditsEarned;
    }

    public static BigDecimal calculateLatestSemesterGpa(List<Grade> grades) {
        if (grades == null || grades.isEmpty()) return null;
        Grade latest = grades.stream()
                .filter(grade -> grade != null && grade.getStatus() == GradeStatus.APPROVED)
                .filter(grade -> grade.getCourse() != null && grade.getCourse().getSemester() != null)
                .max(java.util.Comparator.comparing(
                        Grade::getApprovedAt,
                        java.util.Comparator.nullsFirst(java.util.Comparator.naturalOrder())))
                .orElse(null);
        if (latest == null) return null;
        String semester = latest.getCourse().getSemester();
        BigDecimal weightedTotal = BigDecimal.ZERO;
        int totalCredits = 0;
        for (Grade grade : grades) {
            if (grade == null || grade.getStatus() != GradeStatus.APPROVED
                    || grade.getCourse() == null || !semester.equals(grade.getCourse().getSemester())) continue;
            if (grade.getOriginalAttempt() != null
                    || (grade.getAttemptNumber() != null && grade.getAttemptNumber() > 1)) {
                return null;
            }
            if (!UgandanTertiaryGradingPolicy.VERSION.equals(grade.getGradingPolicyVersion())
                    || grade.getGpaScore() == null
                    || grade.getGpaScore().compareTo(gradeToGpa(grade.getGradeValue())) != 0) {
                throw new IllegalStateException("Semester GPA cannot include an unverified grade.");
            }
            Integer credits = grade.getCourse().getCredits();
            if (credits == null || credits <= 0) {
                throw new IllegalStateException("Approved grades require a positive course credit value.");
            }
            weightedTotal = weightedTotal.add(grade.getGpaScore().multiply(BigDecimal.valueOf(credits)));
            totalCredits += credits;
        }
        return totalCredits == 0 ? null
                : weightedTotal.divide(BigDecimal.valueOf(totalCredits), 2, RoundingMode.HALF_UP);
    }

    public static String latestApprovedSemester(List<Grade> grades) {
        if (grades == null) return null;
        return grades.stream()
                .filter(grade -> grade != null && grade.getStatus() == GradeStatus.APPROVED)
                .filter(grade -> grade.getCourse() != null && grade.getCourse().getSemester() != null)
                .max(java.util.Comparator.comparing(
                        Grade::getApprovedAt,
                        java.util.Comparator.nullsFirst(java.util.Comparator.naturalOrder())))
                .map(grade -> grade.getCourse().getSemester())
                .orElse(null);
    }
}