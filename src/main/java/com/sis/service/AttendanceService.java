package com.sis.service;

import com.sis.dto.AttendanceApprovalRequest;
import com.sis.entity.Attendance;
import com.sis.entity.Course;
import com.sis.entity.Student;
import com.sis.entity.Teacher;
import com.sis.entity.User;
import com.sis.exception.ResourceNotFoundException;
import com.sis.repository.AttendanceRepository;
import com.sis.repository.CourseRepository;
import com.sis.repository.TeacherRepository;
import com.sis.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Attendance Service - Manages student attendance records.
 * Handles attendance tracking, recording, and reporting.
 */
@Service
@Transactional
public class AttendanceService {

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private StudentService studentService;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private TeacherRepository teacherRepository;

    @Autowired
    private UserRepository userRepository;

    /**
     * Get all attendance records for student.
     */
    public List<Attendance> getStudentAttendance(Long studentId) {
        studentService.getStudentById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found with ID: " + studentId));
        return attendanceRepository.findByStudentIdWithCourse(studentId);
    }

    @Transactional(readOnly = true)
    public List<Attendance> getCourseAttendance(Long courseId, LocalDate date) {
        if (!courseRepository.existsById(courseId)) {
            throw new ResourceNotFoundException("Course not found with ID: " + courseId);
        }
        return attendanceRepository.findByCourseIdAndDateWithStudentAndCourse(courseId, date);
    }

    @Transactional(readOnly = true)
    public List<Attendance> getAllAttendance() {
        return attendanceRepository.findAllWithStudentAndCourse();
    }

        public List<Attendance> approveTeacherRosterAttendance(String teacherUsername, AttendanceApprovalRequest request) {
        String normalizedUsername = teacherUsername == null ? null : teacherUsername.trim();
        User user = userRepository.findByUsernameIgnoreCase(normalizedUsername)
            .orElseThrow(() -> new ResourceNotFoundException("Teacher account not found: " + teacherUsername));
        Teacher teacher = teacherRepository.findByUserId(user.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Teacher profile not found for user: " + teacherUsername));
        Course course = courseRepository.findById(request.getCourseId())
            .orElseThrow(() -> new ResourceNotFoundException("Course not found with ID: " + request.getCourseId()));
        if (course.getTeacher() == null || !course.getTeacher().getId().equals(teacher.getId())) {
            throw new ResourceNotFoundException("Teacher is not assigned to this course.");
        }

        Set<Long> enrolledIds = course.getStudents().stream().map(Student::getId).collect(Collectors.toSet());
        Map<Long, AttendanceApprovalRequest.StudentAttendanceMark> submitted = request.getRecords().stream()
            .collect(Collectors.toMap(AttendanceApprovalRequest.StudentAttendanceMark::getStudentId, mark -> mark,
                (first, ignored) -> first));
        if (submitted.size() != request.getRecords().size() || !enrolledIds.equals(submitted.keySet())) {
            throw new IllegalArgumentException("Attendance must include every enrolled student exactly once.");
        }
        if (request.getRecords().stream().anyMatch(mark -> mark.getPresent() == null)) {
            throw new IllegalArgumentException("An attendance status is required for every enrolled student.");
        }
        if (request.getRecords().stream().anyMatch(mark -> mark.isLate() && !Boolean.TRUE.equals(mark.getPresent()))) {
            throw new IllegalArgumentException("A student marked late must also be marked present.");
        }

        course.getStudents().forEach(student -> {
            AttendanceApprovalRequest.StudentAttendanceMark mark = submitted.get(student.getId());
            List<Attendance> existing = attendanceRepository.findByStudentIdAndCourseIdAndDate(
                student.getId(), course.getId(), request.getDate());
            Attendance attendance = existing.isEmpty()
                ? new Attendance(student, course, request.getDate(), Boolean.TRUE.equals(mark.getPresent()))
                : existing.get(0);
            attendance.setPresent(Boolean.TRUE.equals(mark.getPresent()));
            attendance.setRemarks(mark.getRemarks());
            attendance.setLate(mark.isLate());
            attendanceRepository.save(attendance);
        });
        return attendanceRepository.findByCourseIdAndDateWithStudentAndCourse(course.getId(), request.getDate());
        }

    /**
     * Calculate attendance percentage for student in course.
     * <p>
     * This used to be hardcoded to always return {@code 85.5f} regardless of input
     * (the original comment said "For now, returning a placeholder"). Now computed for
     * real: present records / total records for that student+course, as a 0-100 float.
     * Returns 0 if the student has no attendance records for the course yet, rather
     * than dividing by zero.
     *
     * @return attendance percentage (0-100)
     */
    public float getAttendancePercentage(Long studentId, Long courseId) {
        // Validate the student exists (keeps the same 404 behavior callers already expect)
        studentService.getStudentById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found with ID: " + studentId));
        if (!courseRepository.existsById(courseId)) {
            throw new ResourceNotFoundException("Course not found with ID: " + courseId);
        }

        List<Attendance> records = attendanceRepository.findByStudentIdAndCourseId(studentId, courseId);
        if (records.isEmpty()) {
            return 0f;
        }
        long presentCount = records.stream().filter(Attendance::isPresent).count();
        return (presentCount * 100f) / records.size();
    }

    /**
     * Get attendance records between dates.
     */
    public List<Attendance> getAttendanceByDateRange(Long studentId, LocalDate startDate, LocalDate endDate) {
        studentService.getStudentById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found with ID: " + studentId));
        return attendanceRepository.findByStudentIdAndDateRangeWithStudentAndCourse(studentId, startDate, endDate);
    }

}
