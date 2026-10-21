package com.sis.service;

import com.sis.entity.Grade;
import com.sis.entity.Student;
import com.sis.entity.User;
import com.sis.repository.GradeRepository;
import com.sis.repository.StudentRepository;
import com.sis.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Service class for Student entity operations
 * Handles CRUD operations, CGPA calculations, and student profile management
 */
@Service
@Transactional
public class StudentService {

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GradeRepository gradeRepository;

    /**
     * Get all active students
     * @return list of active students
     */
    public List<Student> getAllStudents() {
        return studentRepository.findAll();
    }

    /**
     * Get student by ID if active
     * @param id student ID
     * @return Optional containing student if found and active
     */
    public Optional<Student> getStudentById(Long id) {
        return studentRepository.findById(id);
    }

    /**
     * Get student by username
     * @param username user's username
     * @return Optional containing student if found
     */
    public Optional<Student> getStudentByUsername(String username) {
        return studentRepository.findByUserUsername(username);
    }

    /**
     * Create new student with associated user account
     * @param student the student to create
     * @return created student
     */
    public Student createStudent(Student student) {
        if (student.getUser() != null && student.getUser().getIsActive() == null) {
            student.getUser().setIsActive(true);
        }
        return studentRepository.save(student);
    }

    /**
     * Update existing student profile information
     * @param id student ID
     * @param updatedStudent updated student information
     * @return updated student
     */
    public Student updateStudent(Long id, Student updatedStudent) {
        Optional<Student> existing = studentRepository.findById(id);
        if (existing.isPresent()) {
            Student student = existing.get();
            student.setFullName(updatedStudent.getFullName());
            student.setClassName(updatedStudent.getClassName());
            student.setEmail(updatedStudent.getEmail());
            student.setPhone(updatedStudent.getPhone());
            student.setAddress(updatedStudent.getAddress());
            student.setEnrollmentDate(updatedStudent.getEnrollmentDate());
            student.setDateOfBirth(updatedStudent.getDateOfBirth());
            student.setGender(updatedStudent.getGender());
            student.setAlternativePhone(updatedStudent.getAlternativePhone());
            student.setCity(updatedStudent.getCity());
            student.setCountry(updatedStudent.getCountry());
            if (updatedStudent.getProfileImagePath() != null) {
                student.setProfileImagePath(updatedStudent.getProfileImagePath());
            }
            return studentRepository.save(student);
        }
        throw new RuntimeException("Student not found with id: " + id);
    }

    /**
     * Soft delete student - marks account as inactive
     * @param id student ID
     */
    public void softDeleteStudent(Long id) {
        Optional<Student> student = studentRepository.findById(id);
        if (student.isPresent()) {
            Student s = student.get();
            s.getUser().setIsActive(false);
            studentRepository.save(s);
        } else {
            throw new RuntimeException("Student not found with id: " + id);
        }
    }

    /**
     * Calculate CGPA for student based on all their grades
     * CGPA = Sum(GPA * Credits) / Total Credits
     * @param studentId student ID
     * @return calculated CGPA
     */
    public BigDecimal calculateCGPA(Long studentId) {
        Optional<Student> student = studentRepository.findById(studentId);
        if (student.isEmpty()) {
            throw new RuntimeException("Student not found with id: " + studentId);
        }

        BigDecimal cgpa = GradeCalculationService.calculateApprovedCgpa(
                gradeRepository.findByStudentId(studentId));
        Student s = student.get();
        s.setCgpa(cgpa);
        studentRepository.save(s);
        return cgpa;
    }

    /**
     * Get all grades for a student
     * @param studentId student ID
     * @return list of grades
     */
    public List<Grade> getStudentGrades(Long studentId) {
        return gradeRepository.findByStudentId(studentId);
    }

    /**
     * Restore soft-deleted student account
     * @param id student ID
     */
    public void restoreStudent(Long id) {
        Optional<Student> student = studentRepository.findById(id);
        if (student.isPresent()) {
            Student s = student.get();
            s.getUser().setIsActive(true);
            studentRepository.save(s);
        } else {
            throw new RuntimeException("Student not found with id: " + id);
        }
    }
}
