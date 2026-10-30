package com.sis;

import com.sis.entity.Course;
import com.sis.entity.Grade;
import com.sis.entity.GradeStatus;
import java.time.LocalDateTime;
import com.sis.entity.ProgramAwardType;
import com.sis.service.GradeCalculationService;
import com.sis.service.UgandanTertiaryGradingPolicy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GradeCalculationServiceTest {

    @Test
    void onlyApprovedGradesContributeUsingCourseCredits() {
        Course threeCreditCourse = new Course();
        threeCreditCourse.setCredits(3);
        Course oneCreditCourse = new Course();
        oneCreditCourse.setCredits(1);

        Grade approved = grade(threeCreditCourse, "A", "5.0", GradeStatus.APPROVED);
        Grade pending = grade(oneCreditCourse, "F", "0.0", GradeStatus.PENDING);
        Grade rejected = grade(oneCreditCourse, "F", "0.0", GradeStatus.REJECTED);

        assertEquals(new BigDecimal("5.00"),
                GradeCalculationService.calculateApprovedCgpa(List.of(approved, pending, rejected)));
    }

    @Test
    void earnedCreditsExcludeFailedUnitsAndSemesterGpaUsesCreditWeighting() {
        Course passedCourse = new Course();
        passedCourse.setCredits(3);
        passedCourse.setSemester("Semester 1");
        Course failedCourse = new Course();
        failedCourse.setCredits(2);
        failedCourse.setSemester("Semester 1");
        Grade passed = grade(passedCourse, "D", "2.0", GradeStatus.APPROVED);
        passed.setApprovedAt(LocalDateTime.parse("2026-06-01T10:00:00"));
        Grade failed = grade(failedCourse, "F", "0.0", GradeStatus.APPROVED);
        failed.setApprovedAt(LocalDateTime.parse("2026-06-02T10:00:00"));

        assertEquals(3, GradeCalculationService.calculateCreditsEarned(List.of(passed, failed)));
        assertEquals("1.20",
                GradeCalculationService.calculateLatestSemesterGpa(List.of(passed, failed)).toPlainString());
        assertEquals("Semester 1", GradeCalculationService.latestApprovedSemester(List.of(passed, failed)));
    }

    @Test
    void gradeConversionIsTrimmedAndCaseInsensitive() {
        assertEquals(new BigDecimal("4.50"), GradeCalculationService.gradeToGpa(" b+ ").setScale(2));
        assertEquals(BigDecimal.ZERO, GradeCalculationService.gradeToGpa("F"));
        assertThrows(IllegalArgumentException.class, () -> GradeCalculationService.gradeToGpa("A-"));
        assertThrows(IllegalArgumentException.class, () -> GradeCalculationService.gradeToGpa("unknown"));
    }

    @Test
    void approvedRetakeCannotBeSilentlyCountedWithoutPolicy() {
        Course course = new Course();
        course.setCredits(3);
        Grade original = grade(course, "F", "0.0", GradeStatus.APPROVED);
        Grade retake = grade(course, "A", "5.0", GradeStatus.APPROVED);
        retake.setAttemptNumber(2);
        retake.setOriginalAttempt(original);

        assertThrows(IllegalStateException.class,
                () -> GradeCalculationService.calculateApprovedCgpa(List.of(original, retake)));
    }

    private Grade grade(Course course, String gradeValue, String gpa, GradeStatus status) {
        Grade grade = new Grade();
        grade.setCourse(course);
        grade.setGradeValue(gradeValue);
        grade.setGpaScore(new BigDecimal(gpa));
        grade.setStatus(status);
        return grade;
    }

    @Test
    void academicStandingRequiresAllRegisteredCoreCoursesAndPassMark() {
        Course coreCourse = new Course();
        coreCourse.setId(7L);
        coreCourse.setCore(true);
        Grade passedCore = grade(coreCourse, "D", "2.0", GradeStatus.APPROVED);

        assertEquals("NP", GradeCalculationService.academicStanding(
                new BigDecimal("2.00"), List.of(passedCore), List.of(7L)));
        assertEquals("PP", GradeCalculationService.academicStanding(
                new BigDecimal("2.00"), List.of(grade(coreCourse, "F", "0.0", GradeStatus.APPROVED)), List.of(7L)));
        assertEquals("PP", GradeCalculationService.academicStanding(
                new BigDecimal("1.99"), List.of(passedCore), List.of(7L)));
        assertEquals("NOT_FINALIZED", GradeCalculationService.academicStanding(
                new BigDecimal("3.00"), List.of(), List.of(7L)));
    }

    @Test
    void awardClassesUsePublishedFivePointBoundaries() {
        assertEquals("First Class (Honours)",
                UgandanTertiaryGradingPolicy.awardClassification(new BigDecimal("4.40"), ProgramAwardType.BACHELOR));
        assertEquals("Credit / Class II (Upper)",
                UgandanTertiaryGradingPolicy.awardClassification(new BigDecimal("3.60"), ProgramAwardType.DIPLOMA));
        assertEquals("Second Class - Lower Division",
                UgandanTertiaryGradingPolicy.awardClassification(new BigDecimal("2.80"), ProgramAwardType.BACHELOR));
        assertEquals("Pass / Class III",
                UgandanTertiaryGradingPolicy.awardClassification(new BigDecimal("2.00"), ProgramAwardType.DIPLOMA));
        assertEquals("Fail / No Award",
                UgandanTertiaryGradingPolicy.awardClassification(new BigDecimal("1.99"), ProgramAwardType.BACHELOR));
    }

    @Test
    void exactPassThresholdAndRetakeRuleAreConsistent() {
        assertTrue(UgandanTertiaryGradingPolicy.isPassingMark(new BigDecimal("50.00")));
        assertFalse(UgandanTertiaryGradingPolicy.requiresRetake(new BigDecimal("50.00")));
        assertFalse(UgandanTertiaryGradingPolicy.isPassingMark(new BigDecimal("49.99")));
        assertTrue(UgandanTertiaryGradingPolicy.requiresRetake(new BigDecimal("49.99")));
    }
}
