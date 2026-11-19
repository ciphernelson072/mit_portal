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
import com.sis.repository.StudentRepository;
import com.sis.repository.AttendanceDisputeRepository;
import com.sis.repository.AuditLogRepository;
import com.sis.dto.AttendanceDisputeDto;
import com.sis.dto.CreateAttendanceDisputeRequest;
import com.sis.dto.ReviewAttendanceDisputeRequest;
import com.sis.entity.AttendanceDispute;
import com.sis.entity.AuditLog;
import com.sis.entity.NotificationCategory;
import com.sis.service.NotificationService;
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

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private AttendanceDisputeRepository disputeRepository;

    @Autowired
    private NotificationService notifications;

    @Autowired
    private AuditLogRepository auditLogs;

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

        List<Student> enrolledStudents = studentRepository.findByCourseIdIncludingRegistrations(course.getId());
        Set<Long> enrolledIds = enrolledStudents.stream().map(Student::getId).collect(Collectors.toSet());
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

        Map<Long, Attendance> existingAttendance = attendanceRepository
                .findByCourseIdAndDateWithStudentAndCourse(course.getId(), request.getDate()).stream()
                .collect(Collectors.toMap(record -> record.getStudent().getId(), record -> record));
        enrolledStudents.forEach(student -> {
            AttendanceApprovalRequest.StudentAttendanceMark mark = submitted.get(student.getId());
            Attendance attendance = existingAttendance.get(student.getId());
            boolean newRecord = attendance == null;
            boolean previouslyPresent = attendance != null && attendance.isPresent();
            boolean previouslyLate = attendance != null && attendance.isLate();
            if (attendance == null) {
                attendance = new Attendance(student, course, request.getDate(),
                        Boolean.TRUE.equals(mark.getPresent()));
            }
            attendance.setPresent(Boolean.TRUE.equals(mark.getPresent()));
            attendance.setRemarks(mark.getRemarks());
            attendance.setLate(mark.isLate());
            Attendance saved = attendanceRepository.save(attendance);
            boolean newlyAbsent = !saved.isPresent() && previouslyPresent;
            boolean newlyLate = saved.isPresent() && saved.isLate() && !previouslyLate;
            boolean newlyRecordedAbsent = newRecord && !saved.isPresent();
            boolean newlyRecordedLate = newRecord && saved.isPresent() && saved.isLate();
            if (newlyAbsent || newlyLate || newlyRecordedAbsent || newlyRecordedLate) {
                notifyStudentOfAttendanceStatus(saved);
            }
        });
        return attendanceRepository.findByCourseIdAndDateWithStudentAndCourse(course.getId(), request.getDate());
        }

    private void notifyStudentOfAttendanceStatus(Attendance attendance) {
        Student student = attendance.getStudent();
        if (student == null || student.getUser() == null || student.getUser().getId() == null) {
            throw new IllegalStateException("Student account is unavailable for attendance status notification.");
        }
        Course course = attendance.getCourse();
        String status = attendance.isPresent() ? "late" : "absent";
        notifications.create(student.getUser().getId(), "Attendance marked " + status,
                "You were marked " + status + " for "
                        + (course.getCourseCode() == null ? course.getName() : course.getCourseCode())
                        + " on " + attendance.getDate() + ".",
                NotificationCategory.ATTENDANCE);
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

    public AttendanceDisputeDto createDispute(String studentUsername, CreateAttendanceDisputeRequest request) {
        Student student = studentRepository.findByUserUsername(studentUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Student profile not found."));
        Attendance attendance = attendanceRepository.findById(request.getAttendanceId())
                .orElseThrow(() -> new ResourceNotFoundException("Attendance record not found."));
        if (!attendance.getStudent().getId().equals(student.getId())) {
            throw new ResourceNotFoundException("Attendance record does not belong to student.");
        }
        if (disputeRepository.existsByAttendanceIdAndStudentIdAndStatusIn(
                attendance.getId(), student.getId(), List.of("OPEN", "ESCALATED"))) {
            throw new IllegalArgumentException("An open dispute already exists for this attendance record.");
        }
        AttendanceDispute dispute = disputeRepository.save(new AttendanceDispute(
                attendance, student, request.getReason(), Boolean.TRUE.equals(request.getRequestedPresent())));
        if (attendance.getCourse().getTeacher() != null
                && attendance.getCourse().getTeacher().getUser() != null) {
            notifications.create(attendance.getCourse().getTeacher().getUser().getId(),
                    "Attendance dispute submitted",
                    student.getFullName() + " disputed attendance on " + attendance.getDate() + ".");
        }
        return toDisputeDto(dispute);
    }

    @Transactional(readOnly = true)
    public List<AttendanceDisputeDto> listStudentDisputes(String username) {
        Student student = studentRepository.findByUserUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Student profile not found."));
        return disputeRepository.findByStudentIdOrderByCreatedAtDesc(student.getId()).stream()
                .map(this::toDisputeDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AttendanceDisputeDto> listCourseDisputes(String teacherUsername, Long courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found."));
        if (course.getTeacher() == null || course.getTeacher().getUser() == null
                || !teacherUsername.equalsIgnoreCase(course.getTeacher().getUser().getUsername())) {
            throw new ResourceNotFoundException("Teacher is not assigned to this course.");
        }
        return disputeRepository.findByCourseId(courseId).stream()
                .map(this::toDisputeDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AttendanceDisputeDto> listAllDisputes() {
        return disputeRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toDisputeDto).collect(Collectors.toList());
    }

    public AttendanceDisputeDto reviewDispute(Long disputeId, ReviewAttendanceDisputeRequest request, String username) {
        AttendanceDispute dispute = disputeRepository.findByIdForUpdate(disputeId)
                .orElseThrow(() -> new ResourceNotFoundException("Attendance dispute not found."));
        User reviewer = userRepository.findByUsernameNormalized(username)
                .orElseThrow(() -> new ResourceNotFoundException("Reviewer not found."));
        if (reviewer.getRole() == com.sis.entity.Role.TEACHER) {
            Teacher teacher = teacherRepository.findByUserId(reviewer.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Teacher profile not found."));
            Course course = dispute.getAttendance().getCourse();
            if (course.getTeacher() == null || !course.getTeacher().getId().equals(teacher.getId())) {
                throw new ResourceNotFoundException("Teacher is not assigned to this attendance course.");
            }
        }
        if (!Set.of("APPROVED", "REJECTED", "ESCALATED").contains(request.getStatus())) {
            throw new IllegalArgumentException("Invalid dispute status.");
        }
        if ("APPROVED".equals(dispute.getStatus()) || "REJECTED".equals(dispute.getStatus())) {
            throw new IllegalStateException("Attendance dispute is already finalized.");
        }
        if (request.getStatus().equals(dispute.getStatus())) {
            return toDisputeDto(dispute);
        }
        dispute.setStatus(request.getStatus());
        dispute.setResolution(request.getResolution());
        dispute.setReviewedBy(reviewer.getId());
        dispute.setReviewedAt(java.time.LocalDateTime.now());
        if ("APPROVED".equals(request.getStatus())) {
            dispute.getAttendance().setPresent(dispute.isRequestedPresent());
            attendanceRepository.save(dispute.getAttendance());
        }
        AttendanceDispute saved = disputeRepository.save(dispute);
        auditLogs.save(new AuditLog(reviewer.getId(), "ATTENDANCE_DISPUTE_REVIEWED",
                "Dispute #" + saved.getId() + " status changed to " + saved.getStatus()));
        if (dispute.getStudent().getUser() != null) {
            notifications.create(dispute.getStudent().getUser().getId(), "Attendance dispute updated",
                    "Dispute #" + saved.getId() + " is " + saved.getStatus() + ".");
        }
        return toDisputeDto(saved);
    }

    private AttendanceDisputeDto toDisputeDto(AttendanceDispute dispute) {
        AttendanceDisputeDto dto = new AttendanceDisputeDto();
        dto.setId(dispute.getId());
        dto.setAttendanceId(dispute.getAttendance().getId());
        dto.setStudentId(dispute.getStudent().getId());
        dto.setStudentName(dispute.getStudent().getFullName());
        dto.setCourseId(dispute.getAttendance().getCourse().getId());
        dto.setCourseName(dispute.getAttendance().getCourse().getName());
        dto.setAttendanceDate(dispute.getAttendance().getDate());
        dto.setRecordedPresent(dispute.getAttendance().isPresent());
        dto.setRequestedPresent(dispute.isRequestedPresent());
        dto.setReason(dispute.getReason());
        dto.setStatus(dispute.getStatus());
        dto.setResolution(dispute.getResolution());
        dto.setCreatedAt(dispute.getCreatedAt());
        dto.setReviewedAt(dispute.getReviewedAt());
        return dto;
    }

}
