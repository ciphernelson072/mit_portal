package com.sis;

import com.sis.entity.Course;
import com.sis.entity.Grade;
import com.sis.entity.GradeStatus;
import com.sis.service.GradeCalculationService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GradeCalculationServiceTest {

    @Test
    void onlyApprovedGradesContributeUsingCourseCredits() {
        Course threeCreditCourse = new Course();
        threeCreditCourse.setCredits(3);
        Course oneCreditCourse = new Course();
        oneCreditCourse.setCredits(1);

        Grade approved = grade(threeCreditCourse, "4.0", GradeStatus.APPROVED);
        Grade pending = grade(oneCreditCourse, "0.0", GradeStatus.PENDING);
        Grade rejected = grade(oneCreditCourse, "0.0", GradeStatus.REJECTED);

        assertEquals(new BigDecimal("4.00"),
                GradeCalculationService.calculateApprovedCgpa(List.of(approved, pending, rejected)));
    }

    @Test
    void gradeConversionIsTrimmedAndCaseInsensitive() {
        assertEquals(new BigDecimal("3.30"), GradeCalculationService.gradeToGpa(" b+ ").setScale(2));
        assertEquals(BigDecimal.ZERO, GradeCalculationService.gradeToGpa("unknown"));
    }

    private Grade grade(Course course, String gpa, GradeStatus status) {
        Grade grade = new Grade();
        grade.setCourse(course);
        grade.setGpaScore(new BigDecimal(gpa));
        grade.setStatus(status);
        return grade;
    }
}
