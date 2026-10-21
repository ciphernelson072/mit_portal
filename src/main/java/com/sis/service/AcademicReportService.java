package com.sis.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sis.dto.AcademicReportDto;
import com.sis.dto.CourseGradeDto;
import com.sis.entity.AcademicReportApproval;
import com.sis.entity.Grade;
import com.sis.entity.GradeStatus;
import com.sis.entity.Student;
import com.sis.exception.ResourceNotFoundException;
import com.sis.repository.AcademicReportApprovalRepository;
import com.sis.repository.GradeRepository;
import com.sis.repository.StudentRepository;
import com.sis.repository.TeacherRepository;

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

    @Autowired
    private AcademicReportApprovalRepository approvalRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private TeacherRepository teacherRepository;

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
        List<Grade> grades = gradeRepository.findByStudentIdAndStatus(studentId, GradeStatus.APPROVED);
        report.setCgpa(grades.isEmpty() ? null : GradeCalculationService.calculateApprovedCgpa(grades));
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
        Optional<AcademicReportApproval> approval = approvalRepository.findByStudentId(studentId);
        if (approval.isPresent()) {
            report.setApproved(Boolean.TRUE);
            report.setApprovedBy(approval.get().getApprovedBy());
            report.setApprovedAt(formatApprovalTimestamp(approval.get().getApprovedAt()));
        } else {
            report.setApproved(Boolean.FALSE);
            report.setApprovedBy(null);
            report.setApprovedAt(null);
        }

        // Generate performance summary
        String summary = generatePerformanceSummary(student, report);
        report.setPerformanceSummary(summary);

        logger.info("Academic report generated for student: " + student.getFullName());
        return report;
    }

    public AcademicReportDto approveAcademicReport(Long studentId, String approverUsername) {
        AcademicReportDto report = generateAcademicReport(studentId);
        LocalDateTime approvedAt = LocalDateTime.now();
        approvalRepository.save(new AcademicReportApproval(studentId, approverUsername, approvedAt));
        report.setApproved(Boolean.TRUE);
        report.setApprovedBy(approverUsername);
        report.setApprovedAt(formatApprovalTimestamp(approvedAt));
        logger.info("Academic report approved for student " + studentId + " by " + approverUsername);
        return report;
    }

    public boolean isOfficiallyApproved(Long studentId) {
        return approvalRepository.existsByStudentId(studentId);
    }

    public AcademicReportDto getApprovalStatus(Long studentId) {
        return generateAcademicReport(studentId);
    }

    private String formatApprovalTimestamp(LocalDateTime timestamp) {
        return timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
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
        summary.append("Student Performance Summary for ").append(student.getFullName()).append("\n");
        summary.append("==========================================\n");
        summary.append("Total Courses: ").append(report.getTotalCourses()).append("\n");
        if (cgpa == null) {
            summary.append("Performance Level: Not available (no approved grades)\n");
            summary.append("Current CGPA: Not available\n");
            return summary.toString();
        }

        summary.append("Performance Level: ").append(getPerformanceLevel(cgpa)).append("\n");
        summary.append("Current CGPA: ").append(cgpa).append("\n");

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
        List<Student> students = studentRepository.findByClassName(className);
        List<Map.Entry<Student, BigDecimal>> gradedStudents = students.stream()
            .map(student -> {
                BigDecimal cgpa = approvedCgpa(student.getId());
                return cgpa == null ? null : Map.entry(student, cgpa);
            })
            .filter(java.util.Objects::nonNull)
            .toList();
        BigDecimal averageCgpa = average(gradedStudents.stream().map(Map.Entry::getValue).toList());
        Map.Entry<Student, BigDecimal> topper = gradedStudents.stream()
            .max(Map.Entry.comparingByValue())
            .orElse(null);
        StringBuilder stats = new StringBuilder();
        stats.append("Class Statistics for ").append(className).append("\n");
        stats.append("Total Students: ").append(students.size()).append("\n");
        stats.append("Students with approved grades: ").append(gradedStudents.size()).append("\n");
        stats.append("Average CGPA: ").append(averageCgpa == null ? "N/A" : averageCgpa).append("\n");
        stats.append("Topper: ").append(topper == null ? "N/A"
                : topper.getKey().getFullName() + " (" + topper.getValue() + ")").append("\n");
        return stats.toString();
    }

    /**
     * Get teacher performance statistics
     * @param teacherId teacher ID
     * @return statistics for teacher
     */
    public String getTeacherStatistics(Long teacherId) {
        var teacher = teacherRepository.findById(teacherId)
            .orElseThrow(() -> new ResourceNotFoundException("Teacher not found with ID: " + teacherId));
        List<Student> students = teacher.getCourses().stream()
            .flatMap(course -> course.getStudents().stream())
            .distinct()
            .toList();
        BigDecimal averageCgpa = average(students.stream()
            .map(student -> approvedCgpa(student.getId()))
            .filter(java.util.Objects::nonNull)
            .toList());
        StringBuilder stats = new StringBuilder();
        stats.append("Teacher Performance Statistics\n");
        stats.append("Teacher: ").append(teacher.getFullName()).append("\n");
        stats.append("Total Students Taught: ").append(students.size()).append("\n");
        stats.append("Average Student Performance: ").append(averageCgpa == null ? "N/A" : averageCgpa).append("\n");
        stats.append("Courses Taught: ").append(teacher.getCourses().size()).append("\n");
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
        comparison.append("Student 1: ").append(report1.getStudentName()).append(" (CGPA: ")
                .append(report1.getCgpa() == null ? "Not available" : report1.getCgpa()).append(")\n");
        comparison.append("Student 2: ").append(report2.getStudentName()).append(" (CGPA: ")
                .append(report2.getCgpa() == null ? "Not available" : report2.getCgpa()).append(")\n");

        if (report1.getCgpa() == null || report2.getCgpa() == null) {
            comparison.append("\nCGPA comparison is unavailable until both students have approved grades.\n");
        } else if (report1.getCgpa().compareTo(report2.getCgpa()) > 0) {
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
        int resultLimit = Math.max(1, Math.min(limit, 100));
        List<Student> classStudents = studentRepository.findByClassName(className);
        List<Map.Entry<Student, BigDecimal>> students = classStudents.stream()
                .map(student -> {
                    BigDecimal cgpa = approvedCgpa(student.getId());
                    return cgpa == null ? null : Map.entry(student, cgpa);
                })
                .filter(java.util.Objects::nonNull)
                .sorted(Map.Entry.<Student, BigDecimal>comparingByValue().reversed())
                .limit(resultLimit)
                .toList();
        StringBuilder topStudents = new StringBuilder();
        topStudents.append("Top ").append(resultLimit).append(" Students in ").append(className).append("\n");
        topStudents.append("==========================================\n");
        if (students.isEmpty()) {
            topStudents.append(classStudents.isEmpty() ? "No students found.\n" : "No approved grades available.\n");
        } else {
            for (int index = 0; index < students.size(); index++) {
                Map.Entry<Student, BigDecimal> student = students.get(index);
                topStudents.append(index + 1).append(". ").append(student.getKey().getFullName())
                        .append(" - CGPA: ").append(student.getValue()).append("\n");
            }
        }
        return topStudents.toString();
    }

    private BigDecimal approvedCgpa(Long studentId) {
        List<Grade> approved = gradeRepository.findByStudentIdAndStatus(studentId, GradeStatus.APPROVED);
        return approved.isEmpty() ? null : GradeCalculationService.calculateApprovedCgpa(approved);
    }

    private BigDecimal average(List<BigDecimal> values) {
        if (values.isEmpty()) return null;
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }
}
