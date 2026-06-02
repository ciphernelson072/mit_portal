package com.sis.service;

import com.sis.entity.Grade;
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
            grade.setGradeValue(updatedGrade.getGradeValue());
            grade.setGpaScore(updatedGrade.getGpaScore());
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
            Long studentId = grade.get().getStudent().getId();
            gradeRepository.deleteById(id);
            
            // Recalculate student's CGPA after removing grade
            updateStudentCGPA(studentId);
        } else {
            throw new RuntimeException("Grade not found with id: " + id);
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
        BigDecimal cgpa = calculateCGPA(grades);

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
    private BigDecimal calculateCGPA(List<Grade> grades) {
        if (grades.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal totalWeightedGpa = BigDecimal.ZERO;
        int totalCredits = 0;

        // Calculate weighted GPA: sum of (GPA * Credits)
        for (Grade grade : grades) {
            BigDecimal gpa = grade.getGpaScore();
            Integer credits = grade.getCourse().getCredits();
            if (credits == null) credits = 3;

            totalWeightedGpa = totalWeightedGpa.add(gpa.multiply(new BigDecimal(credits)));
            totalCredits += credits;
        }

        // CGPA = Total Weighted GPA / Total Credits
        if (totalCredits > 0) {
            return totalWeightedGpa.divide(new BigDecimal(totalCredits), 2, java.math.RoundingMode.HALF_UP);
        }

        return BigDecimal.ZERO;
    }

    /**
     * Get GPA for a specific course
     * Converts letter grade to numeric GPA (A=4.0, B=3.0, C=2.0, D=1.0, F=0.0)
     * @param letterGrade the letter grade
     * @return numeric GPA equivalent
     */
    public BigDecimal getGPAFromGrade(String letterGrade) {
        if (letterGrade == null) return BigDecimal.ZERO;

        return switch (letterGrade.toUpperCase()) {
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
            case "F" -> new BigDecimal("0.0");
            default -> BigDecimal.ZERO;
        };
    }
}
