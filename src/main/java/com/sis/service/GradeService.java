package com.sis.service;

import com.sis.entity.Grade;
import com.sis.entity.GradeStatus;
import com.sis.entity.Student;
import com.sis.repository.GradeRepository;
import com.sis.repository.StudentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Service class for Grade management
 * Handles grade recording, GPA calculations, and CGPA updates
 */
@Service
@Transactional
public class GradeService {

    @Autowired
    private GradeRepository gradeRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private AcademicResultCorrectionService resultCorrections;

    /**
     * Get all grades for a student
     * @param studentId student ID
     * @return list of grades
     */
    public List<Grade> getStudentGrades(Long studentId) {
        return gradeRepository.findByStudentId(studentId);
    }

    /**
     * Get a specific grade by ID
     * @param id grade ID
     * @return Optional containing grade if found
     */
    public Optional<Grade> getGradeById(Long id) {
        return gradeRepository.findById(id);
    }

    /**
     * Record a new grade for a student
     * @param grade the grade to record
     * @return saved grade
     */
    public Grade recordGrade(Grade grade) {
        BigDecimal mappedGradePoint = GradeCalculationService.gradeToGpa(grade.getGradeValue());
        grade.setGpaScore(mappedGradePoint);
        grade.setGradingPolicyVersion(UgandanTertiaryGradingPolicy.VERSION);
        grade.setRetakeRequired(mappedGradePoint.compareTo(UgandanTertiaryGradingPolicy.PASS_GRADE_POINT) < 0);
        Grade savedGrade = gradeRepository.save(grade);
        
        // Automatically recalculate student's CGPA after adding grade
        updateStudentCGPA(savedGrade.getStudent().getId());
        
        return savedGrade;
    }

    /**
     * Update an existing grade
     * @param id grade ID
     * @param updatedGrade updated grade information
     * @return updated grade
     */
    public Grade updateGrade(Long id, Grade updatedGrade) {
        Optional<Grade> existing = gradeRepository.findById(id);
        if (existing.isPresent()) {
            Grade grade = existing.get();
            ensureGradeIsNotApproved(grade, "updated");
            grade.setGradeValue(updatedGrade.getGradeValue());
            BigDecimal mappedGradePoint = GradeCalculationService.gradeToGpa(updatedGrade.getGradeValue());
            if (updatedGrade.getGpaScore() != null && updatedGrade.getGpaScore().compareTo(mappedGradePoint) != 0) {
                throw new IllegalArgumentException("Grade points are calculated from the institutional grade mapping.");
            }
            grade.setGpaScore(mappedGradePoint);
            grade.setGradingPolicyVersion(UgandanTertiaryGradingPolicy.VERSION);
            grade.setRetakeRequired(mappedGradePoint.compareTo(UgandanTertiaryGradingPolicy.PASS_GRADE_POINT) < 0);
            grade.setRemarks(updatedGrade.getRemarks());
            
            Grade savedGrade = gradeRepository.save(grade);
            
            // Recalculate student's CGPA after updating grade
            updateStudentCGPA(savedGrade.getStudent().getId());
            
            return savedGrade;
        }
        throw new RuntimeException("Grade not found with id: " + id);
    }

    /**
     * Delete a grade (hard delete for audit purposes)
     * @param id grade ID
     */
    public void deleteGrade(Long id) {
        Optional<Grade> grade = gradeRepository.findById(id);
        if (grade.isPresent()) {
            ensureGradeIsNotApproved(grade.get(), "deleted");
            if (resultCorrections != null && resultCorrections.hasHistory(id)) {
                throw new IllegalStateException("Grades with correction history cannot be deleted.");
            }
            Long studentId = grade.get().getStudent().getId();
            gradeRepository.deleteById(id);
            
            // Recalculate student's CGPA after removing grade
            updateStudentCGPA(studentId);
        } else {
            throw new RuntimeException("Grade not found with id: " + id);
        }
    }

    private void ensureGradeIsNotApproved(Grade grade, String action) {
        if (grade.getStatus() == GradeStatus.APPROVED) {
            throw new IllegalStateException(
                    "Approved grades cannot be " + action + " directly. Use the controlled correction workflow.");
        }
    }

    /**
     * Update student's CGPA based on all their grades
     * CGPA = Sum(GPA * Credits) / Total Credits
     * @param studentId student ID
     * @return updated CGPA
     */
    public BigDecimal updateStudentCGPA(Long studentId) {
        Optional<Student> student = studentRepository.findById(studentId);
        if (student.isEmpty()) {
            throw new RuntimeException("Student not found with id: " + studentId);
        }

        List<Grade> grades = gradeRepository.findByStudentId(studentId);
        BigDecimal cgpa = GradeCalculationService.calculateApprovedCgpa(grades);

        Student s = student.get();
        s.setCgpa(cgpa);
        studentRepository.save(s);

        return cgpa;
    }

    /**
     * Calculate CGPA from a list of grades
     * @param grades list of grades
     * @return calculated CGPA
     */

    /**
     * Get GPA for a specific course
     * Converts letter grade to numeric GPA (A=4.0, B=3.0, C=2.0, D=1.0, F=0.0)
     * @param letterGrade the letter grade
     * @return numeric GPA equivalent
     */
    public BigDecimal getGPAFromGrade(String letterGrade) {
        return GradeCalculationService.gradeToGpa(letterGrade);
    }
}
