package com.sis.service;

import com.sis.dto.AcademicReportDto;
import com.sis.dto.CourseGradeDto;
import com.sis.entity.Student;
import com.sis.entity.Grade;
import com.sis.exception.ResourceNotFoundException;
import com.sis.repository.GradeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Academic Report Service - Generates comprehensive academic reports for students
 * Provides performance analysis, CGPA tracking, and academic statistics
 */
@Service
@Transactional
public class AcademicReportService {

    private static final Logger logger = Logger.getLogger(AcademicReportService.class.getName());

    @Autowired
    private StudentService studentService;

    @Autowired
    private GradeRepository gradeRepository;

    /**
     * Generate comprehensive academic report for student
     * @param studentId student ID
     * @return academic report DTO
     */
    public AcademicReportDto generateAcademicReport(Long studentId) {
        Student student = studentService.getStudentById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found with ID: " + studentId));

        AcademicReportDto report = new AcademicReportDto();
        report.setStudentId(student.getId());
        report.setStudentName(student.getFullName());
        report.setClassName(student.getClassName());
        report.setCgpa(student.getCgpa() != null ? student.getCgpa() : BigDecimal.ZERO);

        // Get all grades for student
        List<Grade> grades = gradeRepository.findByStudent(student);
        report.setTotalCourses(grades.size());

        // Build course grades list
        List<CourseGradeDto> courseGrades = new ArrayList<>();
        for (Grade grade : grades) {
            CourseGradeDto courseGrade = new CourseGradeDto();
            courseGrade.setCourseId(grade.getCourse().getId());
            courseGrade.setCourseName(grade.getCourse().getName());
            courseGrade.setGrade(grade.getGradeValue());
            courseGrade.setGpaScore(grade.getGpaScore());
            courseGrade.setSemester(grade.getCourse().getSemester());
            courseGrade.setCredits(grade.getCourse().getCredits());
            courseGrade.setTeacherName(grade.getCourse().getTeacher() != null ? 
                    grade.getCourse().getTeacher().getFullName() : "N/A");

            // Calculate weighted GPA
            if (grade.getGpaScore() != null && grade.getCourse().getCredits() != null) {
                BigDecimal weighted = grade.getGpaScore()
                        .multiply(new BigDecimal(grade.getCourse().getCredits()));
                courseGrade.setWeightedGpa(weighted);
            }

            courseGrades.add(courseGrade);
        }
        report.setCourseGrades(courseGrades);

        // Calculate average grade
        if (!grades.isEmpty()) {
            String avgGrade = calculateAverageGrade(grades);
            report.setAverageGrade(avgGrade);
        } else {
            report.setAverageGrade("N/A");
        }

        // Set generation date
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        report.setGeneratedDate(LocalDateTime.now().format(formatter));

        // Generate performance summary
        String summary = generatePerformanceSummary(student, report);
        report.setPerformanceSummary(summary);

        logger.info("Academic report generated for student: " + student.getFullName());
        return report;
    }

    /**
     * Calculate average grade from list of grades
     * @param grades list of grade objects
     * @return average grade letter
     */
    private String calculateAverageGrade(List<Grade> grades) {
        if (grades.isEmpty()) {
            return "N/A";
        }

        BigDecimal totalGpa = BigDecimal.ZERO;
        for (Grade grade : grades) {
            if (grade.getGpaScore() != null) {
                totalGpa = totalGpa.add(grade.getGpaScore());
            }
        }

        BigDecimal avgGpa = totalGpa.divide(new BigDecimal(grades.size()), 2, RoundingMode.HALF_UP);

        // Convert GPA back to letter
        if (avgGpa.compareTo(new BigDecimal("3.7")) >= 0) return "A";
        if (avgGpa.compareTo(new BigDecimal("3.3")) >= 0) return "A-";
        if (avgGpa.compareTo(new BigDecimal("3.0")) >= 0) return "B+";
        if (avgGpa.compareTo(new BigDecimal("2.7")) >= 0) return "B";
        if (avgGpa.compareTo(new BigDecimal("2.3")) >= 0) return "B-";
        if (avgGpa.compareTo(new BigDecimal("2.0")) >= 0) return "C+";
        if (avgGpa.compareTo(new BigDecimal("1.7")) >= 0) return "C";
        if (avgGpa.compareTo(new BigDecimal("1.3")) >= 0) return "C-";
        if (avgGpa.compareTo(new BigDecimal("1.0")) >= 0) return "D";
        return "F";
    }

    /**
     * Generate performance summary text
     * @param student the student
     * @param report the academic report
     * @return performance summary
     */
    private String generatePerformanceSummary(Student student, AcademicReportDto report) {
        StringBuilder summary = new StringBuilder();

        BigDecimal cgpa = report.getCgpa();
        String performanceLevel = getPerformanceLevel(cgpa);

        summary.append("Student Performance Summary for ").append(student.getFullName()).append("\n");
        summary.append("==========================================\n");
        summary.append("Performance Level: ").append(performanceLevel).append("\n");
        summary.append("Current CGPA: ").append(cgpa).append("\n");
        summary.append("Total Courses: ").append(report.getTotalCourses()).append("\n");

        if (cgpa.compareTo(new BigDecimal("3.5")) >= 0) {
            summary.append("Status: Excellent performance. Continue with the current approach.\n");
        } else if (cgpa.compareTo(new BigDecimal("3.0")) >= 0) {
            summary.append("Status: Very Good performance. Maintain consistency.\n");
        } else if (cgpa.compareTo(new BigDecimal("2.0")) >= 0) {
            summary.append("Status: Good performance. Focus on challenging areas.\n");
        } else {
            summary.append("Status: Needs Improvement. Seek academic support.\n");
        }

        return summary.toString();
    }

    /**
     * Get performance level based on CGPA
     * @param cgpa cumulative GPA
     * @return performance level
     */
    private String getPerformanceLevel(BigDecimal cgpa) {
        if (cgpa.compareTo(new BigDecimal("3.7")) >= 0) return "Excellent";
        if (cgpa.compareTo(new BigDecimal("3.0")) >= 0) return "Very Good";
        if (cgpa.compareTo(new BigDecimal("2.0")) >= 0) return "Good";
        if (cgpa.compareTo(new BigDecimal("1.0")) >= 0) return "Fair";
        return "Poor";
    }

    /**
     * Get class-wise academic statistics
     * @param className class name
     * @return statistics for the class
     */
    public String getClassStatistics(String className) {
        // This would need to query students by class
        StringBuilder stats = new StringBuilder();
        stats.append("Class Statistics for ").append(className).append("\n");
        stats.append("Total Students: [PLACEHOLDER]\n");
        stats.append("Average CGPA: [PLACEHOLDER]\n");
        stats.append("Topper: [PLACEHOLDER]\n");
        return stats.toString();
    }

    /**
     * Get teacher performance statistics
     * @param teacherId teacher ID
     * @return statistics for teacher
     */
    public String getTeacherStatistics(Long teacherId) {
        StringBuilder stats = new StringBuilder();
        stats.append("Teacher Performance Statistics\n");
        stats.append("Total Students Taught: [PLACEHOLDER]\n");
        stats.append("Average Student Performance: [PLACEHOLDER]\n");
        stats.append("Courses Taught: [PLACEHOLDER]\n");
        return stats.toString();
    }

    /**
     * Generate comparison report between students
     * @param studentId1 first student ID
     * @param studentId2 second student ID
     * @return comparison report
     */
    public String generateComparisonReport(Long studentId1, Long studentId2) {
        AcademicReportDto report1 = generateAcademicReport(studentId1);
        AcademicReportDto report2 = generateAcademicReport(studentId2);

        StringBuilder comparison = new StringBuilder();
        comparison.append("Academic Comparison Report\n");
        comparison.append("==========================================\n");
        comparison.append("Student 1: ").append(report1.getStudentName()).append(" (CGPA: ").append(report1.getCgpa()).append(")\n");
        comparison.append("Student 2: ").append(report2.getStudentName()).append(" (CGPA: ").append(report2.getCgpa()).append(")\n");

        if (report1.getCgpa().compareTo(report2.getCgpa()) > 0) {
            comparison.append("\nStudent 1 has higher CGPA.\n");
        } else if (report1.getCgpa().compareTo(report2.getCgpa()) < 0) {
            comparison.append("\nStudent 2 has higher CGPA.\n");
        } else {
            comparison.append("\nBoth students have equal CGPA.\n");
        }

        return comparison.toString();
    }

    /**
     * Get top performing students in class
     * @param className class name
     * @param limit number of top students
     * @return list of top students
     */
    public String getTopStudents(String className, int limit) {
        StringBuilder topStudents = new StringBuilder();
        topStudents.append("Top ").append(limit).append(" Students in ").append(className).append("\n");
        topStudents.append("==========================================\n");
        topStudents.append("[PLACEHOLDER - Database query needed]\n");
        return topStudents.toString();
    }
}
