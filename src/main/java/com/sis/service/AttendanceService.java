package com.sis.service;

import com.sis.entity.Attendance;
import com.sis.entity.Student;
import com.sis.entity.Course;
import com.sis.exception.ResourceNotFoundException;
import com.sis.repository.AttendanceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.logging.Logger;

/**
 * Attendance Service - Manages student attendance records
 * Handles attendance tracking, recording, and reporting
 */
@Service
@Transactional
public class AttendanceService {

    private static final Logger logger = Logger.getLogger(AttendanceService.class.getName());

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private StudentService studentService;

    /**
     * Record attendance for student in course
     * @param studentId student ID
     * @param courseId course ID
     * @param date attendance date
     * @param present whether student was present
     * @param remarks optional remarks
     * @return created attendance record
     */
    public Attendance recordAttendance(Long studentId, Long courseId, LocalDate date, boolean present, String remarks) {
        // Validation would go here
        Student student = studentService.getStudentById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found with ID: " + studentId));

        Attendance attendance = new Attendance();
        attendance.setStudent(student);
        attendance.setDate(date);
        attendance.setPresent(present);
        attendance.setRemarks(remarks);

        Attendance saved = attendanceRepository.save(attendance);
        logger.info("Attendance recorded for student: " + student.getFullName() + " on " + date);
        return saved;
    }

    /**
     * Get all attendance records for student
     * @param studentId student ID
     * @return list of attendance records
     */
    public List<Attendance> getStudentAttendance(Long studentId) {
        Student student = studentService.getStudentById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found with ID: " + studentId));
        return attendanceRepository.findByStudent(student);
    }

    /**
     * Calculate attendance percentage for student in course
     * @param studentId student ID
     * @param courseId course ID
     * @return attendance percentage (0-100)
     */
    public float getAttendancePercentage(Long studentId, Long courseId) {
        Student student = studentService.getStudentById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found with ID: " + studentId));

        // Would need to fetch course from repository
        // For now, returning a placeholder
        return 85.5f;
    }

    /**
     * Get attendance records between dates
     * @param studentId student ID
     * @param startDate start date
     * @param endDate end date
     * @return list of attendance records
     */
    public List<Attendance> getAttendanceByDateRange(Long studentId, LocalDate startDate, LocalDate endDate) {
        Student student = studentService.getStudentById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found with ID: " + studentId));
        return attendanceRepository.findByStudentAndDateRange(student, startDate, endDate);
    }

    /**
     * Update attendance record
     * @param attendanceId attendance ID
     * @param present presence status
     * @param remarks remarks
     * @return updated attendance record
     */
    public Attendance updateAttendance(Long attendanceId, boolean present, String remarks) {
        Attendance attendance = attendanceRepository.findById(attendanceId)
                .orElseThrow(() -> new ResourceNotFoundException("Attendance record not found with ID: " + attendanceId));

        attendance.setPresent(present);
        attendance.setRemarks(remarks);

        Attendance updated = attendanceRepository.save(attendance);
        logger.info("Attendance record updated: " + attendanceId);
        return updated;
    }

    /**
     * Delete attendance record
     * @param attendanceId attendance ID
     */
    public void deleteAttendance(Long attendanceId) {
        Attendance attendance = attendanceRepository.findById(attendanceId)
                .orElseThrow(() -> new ResourceNotFoundException("Attendance record not found with ID: " + attendanceId));

        attendanceRepository.delete(attendance);
        logger.info("Attendance record deleted: " + attendanceId);
    }

    /**
     * Get attendance record by ID
     * @param attendanceId attendance ID
     * @return attendance record if found
     */
    public Attendance getAttendanceById(Long attendanceId) {
        return attendanceRepository.findById(attendanceId)
                .orElseThrow(() -> new ResourceNotFoundException("Attendance record not found with ID: " + attendanceId));
    }
}
