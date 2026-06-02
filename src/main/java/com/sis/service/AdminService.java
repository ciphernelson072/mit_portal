package com.sis.service;

import com.sis.entity.Role;
import com.sis.entity.Student;
import com.sis.entity.Teacher;
import com.sis.entity.User;
import com.sis.repository.StudentRepository;
import com.sis.repository.TeacherRepository;
import com.sis.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service class for Admin operations
 * Handles full CRUD operations for users, students, and teachers with soft delete support
 */
@Service
@Transactional
public class AdminService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private TeacherRepository teacherRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * Get all active users (excluding soft-deleted)
     * @return list of active users
     */
    public List<User> getAllActiveUsers() {
        return userRepository.findAll().stream()
                .filter(user -> user.getIsActive() != null && user.getIsActive())
                .collect(Collectors.toList());
    }

    /**
     * Get all users (including soft-deleted)
     * @return list of all users
     */
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    /**
     * Get user by ID
     * @param id user ID
     * @return Optional containing user if found
     */
    public Optional<User> getUserById(Long id) {
        return userRepository.findById(id);
    }

    /**
     * Create new user account with encrypted password
     * @param user user to create
     * @return created user
     */
    public User createUser(User user) {
        // Check if username already exists
        if (userRepository.findByUsername(user.getUsername()).isPresent()) {
            throw new RuntimeException("Username already exists: " + user.getUsername());
        }

        // Encrypt password
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setIsActive(true);

        return userRepository.save(user);
    }

    /**
     * Create new student with user account
     * @param student student to create with associated user
     * @return created student
     */
    public Student admitStudent(Student student) {
        // Create user account
        User user = student.getUser();
        user = createUser(user);
        student.setUser(user);

        // Set enrollment date to today
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        student.setEnrollmentDate(LocalDateTime.now().format(formatter));

        return studentRepository.save(student);
    }

    /**
     * Create new teacher with user account
     * @param teacher teacher to create with associated user
     * @return created teacher
     */
    public Teacher hireTeacher(Teacher teacher) {
        // Create user account
        User user = teacher.getUser();
        user = createUser(user);
        teacher.setUser(user);

        return teacherRepository.save(teacher);
    }

    /**
     * Update user account information
     * @param id user ID
     * @param updatedUser updated user information
     * @return updated user
     */
    public User updateUser(Long id, User updatedUser) {
        Optional<User> existing = userRepository.findById(id);
        if (existing.isPresent()) {
            User user = existing.get();
            user.setEmail(updatedUser.getEmail());
            user.setPhone(updatedUser.getPhone());
            
            // Update password only if provided and not null
            if (updatedUser.getPassword() != null && !updatedUser.getPassword().isEmpty()) {
                user.setPassword(passwordEncoder.encode(updatedUser.getPassword()));
            }

            return userRepository.save(user);
        }
        throw new RuntimeException("User not found with id: " + id);
    }

    /**
     * Update student profile
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

            return studentRepository.save(student);
        }
        throw new RuntimeException("Student not found with id: " + id);
    }

    /**
     * Update teacher profile
     * @param id teacher ID
     * @param updatedTeacher updated teacher information
     * @return updated teacher
     */
    public Teacher updateTeacher(Long id, Teacher updatedTeacher) {
        Optional<Teacher> existing = teacherRepository.findById(id);
        if (existing.isPresent()) {
            Teacher teacher = existing.get();
            teacher.setFullName(updatedTeacher.getFullName());
            teacher.setEmail(updatedTeacher.getEmail());
            teacher.setPhone(updatedTeacher.getPhone());
            teacher.setQualification(updatedTeacher.getQualification());
            teacher.setYearsOfExperience(updatedTeacher.getYearsOfExperience());
            teacher.setDepartment(updatedTeacher.getDepartment());

            return teacherRepository.save(teacher);
        }
        throw new RuntimeException("Teacher not found with id: " + id);
    }

    /**
     * Soft delete user - marks account as inactive
     * @param id user ID
     */
    public void removeUser(Long id) {
        Optional<User> user = userRepository.findById(id);
        if (user.isPresent()) {
            User u = user.get();
            u.setIsActive(false);
            userRepository.save(u);
        } else {
            throw new RuntimeException("User not found with id: " + id);
        }
    }

    /**
     * Soft delete student - marks account as inactive
     * @param id student ID
     */
    public void removeStudent(Long id) {
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
     * Soft delete teacher - marks account as inactive
     * @param id teacher ID
     */
    public void removeTeacher(Long id) {
        Optional<Teacher> teacher = teacherRepository.findById(id);
        if (teacher.isPresent()) {
            Teacher t = teacher.get();
            t.getUser().setIsActive(false);
            teacherRepository.save(t);
        } else {
            throw new RuntimeException("Teacher not found with id: " + id);
        }
    }

    /**
     * Restore soft-deleted user account
     * @param id user ID
     */
    public void restoreUser(Long id) {
        Optional<User> user = userRepository.findById(id);
        if (user.isPresent()) {
            User u = user.get();
            u.setIsActive(true);
            userRepository.save(u);
        } else {
            throw new RuntimeException("User not found with id: " + id);
        }
    }

    /**
     * Get count of active students
     * @return number of active students
     */
    public Long getActiveStudentCount() {
        return studentRepository.findAll().stream()
                .filter(s -> s.getUser().getIsActive())
                .count();
    }

    /**
     * Get count of active teachers
     * @return number of active teachers
     */
    public Long getActiveTeacherCount() {
        return teacherRepository.findAll().stream()
                .filter(t -> t.getUser().getIsActive())
                .count();
    }

    /**
     * Get all soft-deleted users (for recovery purposes)
     * @return list of inactive users
     */
    public List<User> getSoftDeletedUsers() {
        return userRepository.findAll().stream()
                .filter(user -> !user.getIsActive())
                .collect(Collectors.toList());
    }
}
