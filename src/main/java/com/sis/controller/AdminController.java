package com.sis.controller;

import com.sis.dto.*;
import com.sis.entity.*;
import com.sis.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Optional;

/**
 * Admin Controller - Handles all administrative operations
 * Full CRUD operations for users, students, teachers with soft delete support
 * All endpoints require ADMIN role authority
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasAuthority('ADMIN')")
public class AdminController {

    @Autowired
    private AdminService adminService;

    @Autowired
    private StudentService studentService;

    @Autowired
    private TeacherService teacherService;

    @Autowired
    private GradeService gradeService;

    // ===== USER MANAGEMENT ENDPOINTS =====

    /**
     * Get all active users
     * @return list of all active users
     */
    @GetMapping("/users")
    public ResponseEntity<List<User>> getAllUsers() {
        List<User> users = adminService.getAllActiveUsers();
        return ResponseEntity.ok(users);
    }

    /**
     * Get user by ID
     * @param id user ID
     * @return user if found
     */
    @GetMapping("/users/{id}")
    public ResponseEntity<User> getUserById(@PathVariable Long id) {
        Optional<User> user = adminService.getUserById(id);
        return user.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Create new user account
     * @param user user to create
     * @return created user
     */
    @PostMapping("/users")
    public ResponseEntity<User> createUser(@Valid @RequestBody User user) {
        User createdUser = adminService.createUser(user);
        return ResponseEntity.ok(createdUser);
    }

    /**
     * Update user information
     * @param id user ID
     * @param user updated user data
     * @return updated user
     */
    @PutMapping("/users/{id}")
    public ResponseEntity<User> updateUser(@PathVariable Long id, @Valid @RequestBody User user) {
        User updatedUser = adminService.updateUser(id, user);
        return ResponseEntity.ok(updatedUser);
    }

    /**
     * Soft delete user (deactivate account)
     * @param id user ID
     * @return success message
     */
    @DeleteMapping("/users/{id}")
    public ResponseEntity<String> removeUser(@PathVariable Long id) {
        adminService.removeUser(id);
        return ResponseEntity.ok("User account deactivated successfully");
    }

    /**
     * Restore soft-deleted user
     * @param id user ID
     * @return success message
     */
    @PostMapping("/users/{id}/restore")
    public ResponseEntity<String> restoreUser(@PathVariable Long id) {
        adminService.restoreUser(id);
        return ResponseEntity.ok("User account restored successfully");
    }

    // ===== STUDENT MANAGEMENT ENDPOINTS =====

    /**
     * Get all students
     * @return list of all students
     */
    @GetMapping("/students")
    public ResponseEntity<List<Student>> getAllStudents() {
        List<Student> students = studentService.getAllStudents();
        return ResponseEntity.ok(students);
    }

    /**
     * Get student by ID
     * @param id student ID
     * @return student if found
     */
    @GetMapping("/students/{id}")
    public ResponseEntity<Student> getStudentById(@PathVariable Long id) {
        Optional<Student> student = studentService.getStudentById(id);
        return student.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Admit (create) new student
     * Creates both student and user account
     * @param student student to admit
     * @return created student
     */
    @PostMapping("/students/admit")
    public ResponseEntity<Student> admitStudent(@Valid @RequestBody Student student) {
        Student admitted = adminService.admitStudent(student);
        return ResponseEntity.ok(admitted);
    }

    /**
     * Update student profile
     * @param id student ID
     * @param student updated student data
     * @return updated student
     */
    @PutMapping("/students/{id}")
    public ResponseEntity<Student> updateStudent(@PathVariable Long id, @Valid @RequestBody Student student) {
        Student updated = adminService.updateStudent(id, student);
        return ResponseEntity.ok(updated);
    }

    /**
     * Soft delete (remove) student
     * Marks student account as inactive
     * @param id student ID
     * @return success message
     */
    @DeleteMapping("/students/{id}")
    public ResponseEntity<String> removeStudent(@PathVariable Long id) {
        adminService.removeStudent(id);
        return ResponseEntity.ok("Student removed successfully");
    }

    /**
     * Get student's CGPA
     * @param id student ID
     * @return student with current CGPA
     */
    @GetMapping("/students/{id}/cgpa")
    public ResponseEntity<StudentCGPADto> getStudentCGPA(@PathVariable Long id) {
        Optional<Student> student = studentService.getStudentById(id);
        if (student.isPresent()) {
            Student s = student.get();
            StudentCGPADto dto = new StudentCGPADto();
            dto.setId(s.getId());
            dto.setFullName(s.getFullName());
            dto.setCgpa(s.getCgpa());
            return ResponseEntity.ok(dto);
        }
        return ResponseEntity.notFound().build();
    }

    /**
     * Recalculate student's CGPA
     * @param id student ID
     * @return updated CGPA
     */
    @PostMapping("/students/{id}/calculate-cgpa")
    public ResponseEntity<String> recalculateCGPA(@PathVariable Long id) {
        studentService.calculateCGPA(id);
        return ResponseEntity.ok("CGPA recalculated successfully");
    }

    // ===== TEACHER MANAGEMENT ENDPOINTS =====

    /**
     * Get all teachers
     * @return list of all teachers
     */
    @GetMapping("/teachers")
    public ResponseEntity<List<Teacher>> getAllTeachers() {
        List<Teacher> teachers = teacherService.getAllTeachers();
        return ResponseEntity.ok(teachers);
    }

    /**
     * Get teacher by ID
     * @param id teacher ID
     * @return teacher if found
     */
    @GetMapping("/teachers/{id}")
    public ResponseEntity<Teacher> getTeacherById(@PathVariable Long id) {
        Optional<Teacher> teacher = teacherService.getTeacherById(id);
        return teacher.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Hire (create) new teacher
     * Creates both teacher and user account
     * @param teacher teacher to hire
     * @return created teacher
     */
    @PostMapping("/teachers/hire")
    public ResponseEntity<Teacher> hireTeacher(@Valid @RequestBody Teacher teacher) {
        Teacher hired = adminService.hireTeacher(teacher);
        return ResponseEntity.ok(hired);
    }

    /**
     * Update teacher profile
     * @param id teacher ID
     * @param teacher updated teacher data
     * @return updated teacher
     */
    @PutMapping("/teachers/{id}")
    public ResponseEntity<Teacher> updateTeacher(@PathVariable Long id, @Valid @RequestBody Teacher teacher) {
        Teacher updated = adminService.updateTeacher(id, teacher);
        return ResponseEntity.ok(updated);
    }

    /**
     * Soft delete (remove) teacher
     * Marks teacher account as inactive
     * @param id teacher ID
     * @return success message
     */
    @DeleteMapping("/teachers/{id}")
    public ResponseEntity<String> removeTeacher(@PathVariable Long id) {
        adminService.removeTeacher(id);
        return ResponseEntity.ok("Teacher removed successfully");
    }

    // ===== STATISTICS AND REPORTING ENDPOINTS =====

    /**
     * Get admin dashboard statistics
     * @return DTO with statistics
     */
    @GetMapping("/statistics")
    public ResponseEntity<AdminStatisticsDto> getStatistics() {
        AdminStatisticsDto stats = new AdminStatisticsDto();
        stats.setTotalStudents(adminService.getActiveStudentCount());
        stats.setTotalTeachers(adminService.getActiveTeacherCount());
        stats.setTotalUsers((long) adminService.getAllActiveUsers().size());
        stats.setDeletedUsersCount((long) adminService.getSoftDeletedUsers().size());
        return ResponseEntity.ok(stats);
    }

    /**
     * Get all soft-deleted users (for recovery)
     * @return list of inactive users
     */
    @GetMapping("/deleted-users")
    public ResponseEntity<List<User>> getDeletedUsers() {
        List<User> deletedUsers = adminService.getSoftDeletedUsers();
        return ResponseEntity.ok(deletedUsers);
    }

}