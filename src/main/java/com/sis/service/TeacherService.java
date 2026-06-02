package com.sis.service;

import com.sis.entity.Course;
import com.sis.entity.Teacher;
import com.sis.entity.User;
import com.sis.repository.CourseRepository;
import com.sis.repository.TeacherRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service class for Teacher entity operations
 * Handles CRUD operations and teacher profile management
 */
@Service
@Transactional
public class TeacherService {

    @Autowired
    private TeacherRepository teacherRepository;

    @Autowired
    private CourseRepository courseRepository;

    /**
     * Get all active teachers
     * @return list of active teachers
     */
    public List<Teacher> getAllTeachers() {
        return teacherRepository.findAll();
    }

    /**
     * Get teacher by ID
     * @param id teacher ID
     * @return Optional containing teacher if found
     */
    public Optional<Teacher> getTeacherById(Long id) {
        return teacherRepository.findById(id);
    }

    /**
     * Get teacher by username
     * @param username user's username
     * @return Optional containing teacher if found
     */
    public Optional<Teacher> getTeacherByUsername(String username) {
        return teacherRepository.findByUserUsername(username);
    }

    /**
     * Create new teacher with associated user account
     * @param teacher the teacher to create
     * @return created teacher
     */
    public Teacher createTeacher(Teacher teacher) {
        if (teacher.getUser() != null && teacher.getUser().getIsActive() == null) {
            teacher.getUser().setIsActive(true);
        }
        return teacherRepository.save(teacher);
    }

    /**
     * Update existing teacher profile information
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
            if (updatedTeacher.getProfileImagePath() != null) {
                teacher.setProfileImagePath(updatedTeacher.getProfileImagePath());
            }
            return teacherRepository.save(teacher);
        }
        throw new RuntimeException("Teacher not found with id: " + id);
    }

    /**
     * Soft delete teacher - marks account as inactive
     * @param id teacher ID
     */
    public void softDeleteTeacher(Long id) {
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
     * Restore soft-deleted teacher account
     * @param id teacher ID
     */
    public void restoreTeacher(Long id) {
        Optional<Teacher> teacher = teacherRepository.findById(id);
        if (teacher.isPresent()) {
            Teacher t = teacher.get();
            t.getUser().setIsActive(true);
            teacherRepository.save(t);
        } else {
            throw new RuntimeException("Teacher not found with id: " + id);
        }
    }

    /**
     * Get all courses taught by a teacher
     * @param teacherId teacher ID
     * @return list of courses
     */
    public List<Course> getTeacherCourses(Long teacherId) {
        Optional<Teacher> teacher = teacherRepository.findById(teacherId);
        if (teacher.isPresent()) {
            return teacher.get().getCourses().stream().toList();
        }
        throw new RuntimeException("Teacher not found with id: " + teacherId);
    }

    /**
     * Get total number of students taught by teacher
     * @param teacherId teacher ID
     * @return count of students
     */
    public Long getStudentCountForTeacher(Long teacherId) {
        Optional<Teacher> teacher = teacherRepository.findById(teacherId);
        if (teacher.isPresent()) {
            return teacher.get().getCourses().stream()
                    .flatMap(course -> course.getStudents().stream())
                    .distinct()
                    .count();
        }
        throw new RuntimeException("Teacher not found with id: " + teacherId);
    }
}
