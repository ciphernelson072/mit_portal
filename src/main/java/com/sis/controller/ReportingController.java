package com.sis.controller;

import com.sis.dto.AcademicReportDto;
import com.sis.service.AcademicReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Reporting Controller - Generates academic and statistical reports
 * Endpoints for academic performance, statistics, and comparisons
 */
@RestController
@RequestMapping("/api/reports")
@CrossOrigin(origins = "*")
public class ReportingController {

    @Autowired
    private AcademicReportService academicReportService;

    /**
     * Generate comprehensive academic report for student
     * @param studentId student ID
     * @return academic report DTO
     */
    @GetMapping("/academic/{studentId}")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('TEACHER') or @studentAccessService.isOwnStudent(#studentId)")
    public ResponseEntity<AcademicReportDto> generateAcademicReport(@PathVariable Long studentId) {
        AcademicReportDto report = academicReportService.generateAcademicReport(studentId);
        return ResponseEntity.ok(report);
    }

    /**
     * Get class-wise academic statistics
     * @param className class name
     * @return statistics report
     */
    @GetMapping("/class/{className}")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('TEACHER')")
    public ResponseEntity<String> getClassStatistics(@PathVariable String className) {
        String statistics = academicReportService.getClassStatistics(className);
        return ResponseEntity.ok(statistics);
    }

    /**
     * Get teacher performance statistics
     * @param teacherId teacher ID
     * @return statistics report
     */
    @GetMapping("/teacher/{teacherId}")
    @PreAuthorize("hasAuthority('ADMIN') or @teacherAccessService.isOwnTeacher(#teacherId)")
    public ResponseEntity<String> getTeacherStatistics(@PathVariable Long teacherId) {
        String statistics = academicReportService.getTeacherStatistics(teacherId);
        return ResponseEntity.ok(statistics);
    }

    /**
     * Generate comparison report between two students
     * @param studentId1 first student ID
     * @param studentId2 second student ID
     * @return comparison report
     */
    @GetMapping("/compare")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<String> compareStudents(
            @RequestParam Long studentId1,
            @RequestParam Long studentId2) {
        String comparison = academicReportService.generateComparisonReport(studentId1, studentId2);
        return ResponseEntity.ok(comparison);
    }

    /**
     * Get top performing students in class
     * @param className class name
     * @param limit number of top students (default 10)
     * @return top students report
     */
    @GetMapping("/top-students")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('TEACHER')")
    public ResponseEntity<String> getTopStudents(
            @RequestParam String className,
            @RequestParam(defaultValue = "10") int limit) {
        String topStudents = academicReportService.getTopStudents(className, limit);
        return ResponseEntity.ok(topStudents);
    }
}
