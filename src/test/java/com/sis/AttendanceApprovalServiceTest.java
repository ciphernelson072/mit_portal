package com.sis;

import com.sis.dto.AttendanceApprovalRequest;
import com.sis.dto.ReviewAttendanceDisputeRequest;
import com.sis.entity.Attendance;
import com.sis.entity.AttendanceDispute;
import com.sis.entity.Course;
import com.sis.entity.Student;
import com.sis.entity.Teacher;
import com.sis.entity.User;
import com.sis.entity.NotificationCategory;
import com.sis.entity.Role;
import com.sis.repository.AttendanceDisputeRepository;
import com.sis.repository.AttendanceRepository;
import com.sis.repository.CourseRepository;
import com.sis.repository.TeacherRepository;
import com.sis.repository.UserRepository;
import com.sis.repository.StudentRepository;
import com.sis.repository.AuditLogRepository;
import com.sis.service.AttendanceService;
import com.sis.service.NotificationService;
import com.sis.service.StudentService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AttendanceApprovalServiceTest {

    @Test
    void teacherApprovalPersistsPresentAbsentAndLateForEveryEnrolledStudent() {
        AttendanceRepository attendanceRepository = Mockito.mock(AttendanceRepository.class);
        CourseRepository courseRepository = Mockito.mock(CourseRepository.class);
        TeacherRepository teacherRepository = Mockito.mock(TeacherRepository.class);
        UserRepository userRepository = Mockito.mock(UserRepository.class);
        StudentRepository studentRepository = Mockito.mock(StudentRepository.class);

        User user = new User();
        user.setId(10L);
        user.setUsername("teacher1");
        Teacher teacher = new Teacher();
        teacher.setId(20L);
        teacher.setUser(user);
        Student presentStudent = student(1L, "Alice");
        Student absentStudent = student(2L, "Bob");
        Student lateStudent = student(3L, "Carol");
        Course course = new Course();
        course.setId(30L);
        course.setCourseCode("ATT101");
        course.setName("Attendance");
        course.setTeacher(teacher);
        course.setStudents(Set.of(presentStudent, absentStudent));
        when(studentRepository.findByCourseIdIncludingRegistrations(30L))
                .thenReturn(List.of(presentStudent, absentStudent, lateStudent));

        when(userRepository.findByUsernameIgnoreCase("teacher1")).thenReturn(Optional.of(user));
        when(teacherRepository.findByUserId(10L)).thenReturn(Optional.of(teacher));
        when(courseRepository.findById(30L)).thenReturn(Optional.of(course));
        Map<Long, Attendance> savedRecords = new LinkedHashMap<>();
        when(attendanceRepository.save(any(Attendance.class))).thenAnswer(invocation -> {
            Attendance attendance = invocation.getArgument(0);
            attendance.setId(100L + attendance.getStudent().getId());
            savedRecords.put(attendance.getStudent().getId(), attendance);
            return attendance;
        });
        when(attendanceRepository.findByCourseIdAndDateWithStudentAndCourse(
                30L, LocalDate.of(2026, 9, 14)))
                .thenAnswer(invocation -> List.copyOf(savedRecords.values()));
        NotificationService notifications = Mockito.mock(NotificationService.class);

        AttendanceService service = new AttendanceService();
        ReflectionTestUtils.setField(service, "attendanceRepository", attendanceRepository);
        ReflectionTestUtils.setField(service, "courseRepository", courseRepository);
        ReflectionTestUtils.setField(service, "teacherRepository", teacherRepository);
        ReflectionTestUtils.setField(service, "userRepository", userRepository);
        ReflectionTestUtils.setField(service, "studentRepository", studentRepository);
        ReflectionTestUtils.setField(service, "studentService", Mockito.mock(StudentService.class));
        ReflectionTestUtils.setField(service, "notifications", notifications);

        AttendanceApprovalRequest request = new AttendanceApprovalRequest();
        request.setCourseId(30L);
        request.setDate(LocalDate.of(2026, 9, 14));
        request.setRecords(List.of(mark(1L, true), mark(2L, false), mark(3L, true, true)));

        List<Attendance> saved = service.approveTeacherRosterAttendance("teacher1", request);
        request.setRecords(List.of(mark(1L, false), mark(2L, false), mark(3L, true, true)));
        service.approveTeacherRosterAttendance("teacher1", request);
        service.approveTeacherRosterAttendance("teacher1", request);

        assertEquals(3, saved.size());
        Mockito.verify(attendanceRepository, Mockito.times(6)).findByCourseIdAndDateWithStudentAndCourse(
                30L, LocalDate.of(2026, 9, 14));
        Mockito.verify(attendanceRepository, Mockito.never())
                .findByStudentIdAndCourseIdAndDate(any(), any(), any());
        ArgumentCaptor<Attendance> captor = ArgumentCaptor.forClass(Attendance.class);
        Mockito.verify(attendanceRepository, Mockito.times(9)).save(captor.capture());
        assertEquals(3, captor.getAllValues().stream().filter(Attendance::isPresent).count());
        assertEquals(6, captor.getAllValues().stream().filter(record -> !record.isPresent()).count());
        assertEquals(3, captor.getAllValues().stream().filter(Attendance::isLate).count());
        verify(notifications, times(1)).create(1002L, "Attendance marked absent",
                "You were marked absent for ATT101 on 2026-09-14.", NotificationCategory.ATTENDANCE);
        verify(notifications, times(1)).create(1003L, "Attendance marked late",
                "You were marked late for ATT101 on 2026-09-14.", NotificationCategory.ATTENDANCE);
        verify(notifications, times(1)).create(1001L, "Attendance marked absent",
                "You were marked absent for ATT101 on 2026-09-14.", NotificationCategory.ATTENDANCE);
        verify(notifications, Mockito.times(3)).create(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.startsWith("Attendance marked "),
                org.mockito.ArgumentMatchers.contains("ATT101 on 2026-09-14."),
                Mockito.eq(NotificationCategory.ATTENDANCE));
    }

    @Test
    void teacherApprovalRejectsIncompleteRoster() {
        AttendanceService service = new AttendanceService();
        AttendanceRepository attendanceRepository = Mockito.mock(AttendanceRepository.class);
        CourseRepository courseRepository = Mockito.mock(CourseRepository.class);
        TeacherRepository teacherRepository = Mockito.mock(TeacherRepository.class);
        UserRepository userRepository = Mockito.mock(UserRepository.class);
        StudentRepository studentRepository = Mockito.mock(StudentRepository.class);
        User user = new User();
        user.setId(10L);
        Teacher teacher = new Teacher();
        teacher.setId(20L);
        Course course = new Course();
        course.setId(30L);
        course.setTeacher(teacher);
        course.setStudents(Set.of(student(1L, "Alice"), student(2L, "Bob")));
        when(studentRepository.findByCourseIdIncludingRegistrations(30L))
                .thenReturn(List.copyOf(course.getStudents()));
        when(userRepository.findByUsernameIgnoreCase("teacher1")).thenReturn(Optional.of(user));
        when(teacherRepository.findByUserId(10L)).thenReturn(Optional.of(teacher));
        when(courseRepository.findById(30L)).thenReturn(Optional.of(course));
        ReflectionTestUtils.setField(service, "attendanceRepository", attendanceRepository);
        ReflectionTestUtils.setField(service, "courseRepository", courseRepository);
        ReflectionTestUtils.setField(service, "teacherRepository", teacherRepository);
        ReflectionTestUtils.setField(service, "userRepository", userRepository);
        ReflectionTestUtils.setField(service, "studentRepository", studentRepository);
        ReflectionTestUtils.setField(service, "studentService", Mockito.mock(StudentService.class));

        AttendanceApprovalRequest request = new AttendanceApprovalRequest();
        request.setCourseId(30L);
        request.setDate(LocalDate.now());
        request.setRecords(List.of(mark(1L, true)));

        assertThrows(IllegalArgumentException.class, () -> service.approveTeacherRosterAttendance("teacher1", request));
    }

    @Test
    void teacherApprovalRejectsDuplicateStudentMarks() {
        AttendanceService service = new AttendanceService();
        AttendanceRepository attendanceRepository = Mockito.mock(AttendanceRepository.class);
        CourseRepository courseRepository = Mockito.mock(CourseRepository.class);
        TeacherRepository teacherRepository = Mockito.mock(TeacherRepository.class);
        UserRepository userRepository = Mockito.mock(UserRepository.class);
        StudentRepository studentRepository = Mockito.mock(StudentRepository.class);
        User user = new User();
        user.setId(10L);
        Teacher teacher = new Teacher();
        teacher.setId(20L);
        Course course = new Course();
        course.setId(30L);
        course.setTeacher(teacher);
        course.setStudents(Set.of(student(1L, "Alice"), student(2L, "Bob")));
        when(studentRepository.findByCourseIdIncludingRegistrations(30L))
                .thenReturn(List.copyOf(course.getStudents()));
        when(userRepository.findByUsernameIgnoreCase("teacher1")).thenReturn(Optional.of(user));
        when(teacherRepository.findByUserId(10L)).thenReturn(Optional.of(teacher));
        when(courseRepository.findById(30L)).thenReturn(Optional.of(course));
        ReflectionTestUtils.setField(service, "attendanceRepository", attendanceRepository);
        ReflectionTestUtils.setField(service, "courseRepository", courseRepository);
        ReflectionTestUtils.setField(service, "teacherRepository", teacherRepository);
        ReflectionTestUtils.setField(service, "userRepository", userRepository);
        ReflectionTestUtils.setField(service, "studentRepository", studentRepository);
        ReflectionTestUtils.setField(service, "studentService", Mockito.mock(StudentService.class));

        AttendanceApprovalRequest request = new AttendanceApprovalRequest();
        request.setCourseId(30L);
        request.setDate(LocalDate.now());
        request.setRecords(List.of(mark(1L, true), mark(1L, false), mark(2L, true)));

        assertThrows(IllegalArgumentException.class, () -> service.approveTeacherRosterAttendance("teacher1", request));
        Mockito.verifyNoInteractions(attendanceRepository);
    }

    @Test
    void teacherApprovalRejectsLateMarkWhenStudentIsAbsent() {
        AttendanceService service = new AttendanceService();
        AttendanceRepository attendanceRepository = Mockito.mock(AttendanceRepository.class);
        CourseRepository courseRepository = Mockito.mock(CourseRepository.class);
        TeacherRepository teacherRepository = Mockito.mock(TeacherRepository.class);
        UserRepository userRepository = Mockito.mock(UserRepository.class);
        StudentRepository studentRepository = Mockito.mock(StudentRepository.class);
        User user = new User();
        user.setId(10L);
        Teacher teacher = new Teacher();
        teacher.setId(20L);
        Course course = new Course();
        course.setId(30L);
        course.setTeacher(teacher);
        course.setStudents(Set.of(student(1L, "Alice"), student(2L, "Bob")));
        when(studentRepository.findByCourseIdIncludingRegistrations(30L))
                .thenReturn(List.copyOf(course.getStudents()));
        when(userRepository.findByUsernameIgnoreCase("teacher1")).thenReturn(Optional.of(user));
        when(teacherRepository.findByUserId(10L)).thenReturn(Optional.of(teacher));
        when(courseRepository.findById(30L)).thenReturn(Optional.of(course));
        ReflectionTestUtils.setField(service, "attendanceRepository", attendanceRepository);
        ReflectionTestUtils.setField(service, "courseRepository", courseRepository);
        ReflectionTestUtils.setField(service, "teacherRepository", teacherRepository);
        ReflectionTestUtils.setField(service, "userRepository", userRepository);
        ReflectionTestUtils.setField(service, "studentRepository", studentRepository);
        ReflectionTestUtils.setField(service, "studentService", Mockito.mock(StudentService.class));

        AttendanceApprovalRequest request = new AttendanceApprovalRequest();
        request.setCourseId(30L);
        request.setDate(LocalDate.now());
        request.setRecords(List.of(mark(1L, false, true), mark(2L, true)));

        assertThrows(IllegalArgumentException.class, () -> service.approveTeacherRosterAttendance("teacher1", request));
        Mockito.verifyNoInteractions(attendanceRepository);
    }

    @Test
    void teacherApprovalRejectsMissingAttendanceStatus() {
        AttendanceService service = new AttendanceService();
        AttendanceRepository attendanceRepository = Mockito.mock(AttendanceRepository.class);
        CourseRepository courseRepository = Mockito.mock(CourseRepository.class);
        TeacherRepository teacherRepository = Mockito.mock(TeacherRepository.class);
        UserRepository userRepository = Mockito.mock(UserRepository.class);
        StudentRepository studentRepository = Mockito.mock(StudentRepository.class);
        User user = new User();
        user.setId(10L);
        Teacher teacher = new Teacher();
        teacher.setId(20L);
        Course course = new Course();
        course.setId(30L);
        course.setTeacher(teacher);
        course.setStudents(Set.of(student(1L, "Alice"), student(2L, "Bob")));
        when(studentRepository.findByCourseIdIncludingRegistrations(30L))
                .thenReturn(List.copyOf(course.getStudents()));
        when(userRepository.findByUsernameIgnoreCase("teacher1")).thenReturn(Optional.of(user));
        when(teacherRepository.findByUserId(10L)).thenReturn(Optional.of(teacher));
        when(courseRepository.findById(30L)).thenReturn(Optional.of(course));
        ReflectionTestUtils.setField(service, "attendanceRepository", attendanceRepository);
        ReflectionTestUtils.setField(service, "courseRepository", courseRepository);
        ReflectionTestUtils.setField(service, "teacherRepository", teacherRepository);
        ReflectionTestUtils.setField(service, "userRepository", userRepository);
        ReflectionTestUtils.setField(service, "studentRepository", studentRepository);
        ReflectionTestUtils.setField(service, "studentService", Mockito.mock(StudentService.class));

        AttendanceApprovalRequest request = new AttendanceApprovalRequest();
        request.setCourseId(30L);
        request.setDate(LocalDate.now());
        request.setRecords(List.of(mark(1L, null), mark(2L, true)));

        assertThrows(IllegalArgumentException.class, () -> service.approveTeacherRosterAttendance("teacher1", request));
        Mockito.verifyNoInteractions(attendanceRepository);
    }

    @Test
    void finalizedAttendanceDisputeCannotBeReviewedAgain() {
        AttendanceDisputeRepository disputes = Mockito.mock(AttendanceDisputeRepository.class);
        AttendanceRepository attendance = Mockito.mock(AttendanceRepository.class);
        UserRepository users = Mockito.mock(UserRepository.class);
        NotificationService notifications = Mockito.mock(NotificationService.class);
        AttendanceDispute dispute = dispute("REJECTED");
        User reviewer = new User("admin", "encoded", Role.ADMIN);
        reviewer.setId(50L);
        when(disputes.findByIdForUpdate(70L)).thenReturn(Optional.of(dispute));
        when(users.findByUsernameNormalized("admin")).thenReturn(Optional.of(reviewer));
        AttendanceService service = attendanceService(disputes, attendance, users, notifications);

        assertThrows(IllegalStateException.class,
                () -> service.reviewDispute(70L, disputeReview("APPROVED"), "admin"));

        verify(disputes).findByIdForUpdate(70L);
        verify(disputes, Mockito.never()).save(any(AttendanceDispute.class));
        verify(attendance, Mockito.never()).save(any(Attendance.class));
        verify(notifications, Mockito.never()).create(any(), any(), any());
    }

    @Test
    void repeatedEscalationDoesNotDuplicateReviewAuditOrNotification() {
        AttendanceDisputeRepository disputes = Mockito.mock(AttendanceDisputeRepository.class);
        AttendanceRepository attendance = Mockito.mock(AttendanceRepository.class);
        UserRepository users = Mockito.mock(UserRepository.class);
        NotificationService notifications = Mockito.mock(NotificationService.class);
        AttendanceDispute dispute = dispute("ESCALATED");
        User reviewer = new User("admin", "encoded", Role.ADMIN);
        reviewer.setId(50L);
        when(disputes.findByIdForUpdate(70L)).thenReturn(Optional.of(dispute));
        when(users.findByUsernameNormalized("admin")).thenReturn(Optional.of(reviewer));
        AttendanceService service = attendanceService(disputes, attendance, users, notifications);

        var result = service.reviewDispute(70L, disputeReview("ESCALATED"), "admin");

        assertEquals("ESCALATED", result.getStatus());
        verify(disputes, Mockito.never()).save(any(AttendanceDispute.class));
        verify(attendance, Mockito.never()).save(any(Attendance.class));
        verify(notifications, Mockito.never()).create(any(), any(), any());
    }

    private static AttendanceService attendanceService(
            AttendanceDisputeRepository disputes, AttendanceRepository attendance,
            UserRepository users, NotificationService notifications) {
        AttendanceService service = new AttendanceService();
        ReflectionTestUtils.setField(service, "disputeRepository", disputes);
        ReflectionTestUtils.setField(service, "attendanceRepository", attendance);
        ReflectionTestUtils.setField(service, "userRepository", users);
        ReflectionTestUtils.setField(service, "teacherRepository", Mockito.mock(TeacherRepository.class));
        ReflectionTestUtils.setField(service, "auditLogs", Mockito.mock(AuditLogRepository.class));
        ReflectionTestUtils.setField(service, "notifications", notifications);
        return service;
    }

    private static AttendanceDispute dispute(String status) {
        Student student = student(21L, "Test Student");
        Course course = new Course();
        course.setId(30L);
        course.setName("Attendance");
        course.setCourseCode("ATT101");
        Attendance record = new Attendance(student, course, LocalDate.of(2026, 9, 14), false);
        record.setId(40L);
        AttendanceDispute dispute = new AttendanceDispute(record, student, "I was present.", true);
        ReflectionTestUtils.setField(dispute, "id", 70L);
        dispute.setStatus(status);
        return dispute;
    }

    private static ReviewAttendanceDisputeRequest disputeReview(String status) {
        ReviewAttendanceDisputeRequest request = new ReviewAttendanceDisputeRequest();
        request.setStatus(status);
        request.setResolution("Review complete.");
        return request;
    }

    private static Student student(Long id, String name) {
        Student student = new Student();
        student.setId(id);
        student.setFullName(name);
        User user = new User("student" + id, "encoded", com.sis.entity.Role.STUDENT);
        user.setId(1000L + id);
        student.setUser(user);
        return student;
    }

    private static AttendanceApprovalRequest.StudentAttendanceMark mark(Long studentId, Boolean present) {
        return mark(studentId, present, false);
    }

    private static AttendanceApprovalRequest.StudentAttendanceMark mark(Long studentId, Boolean present, boolean late) {
        AttendanceApprovalRequest.StudentAttendanceMark mark = new AttendanceApprovalRequest.StudentAttendanceMark();
        mark.setStudentId(studentId);
        mark.setPresent(present);
        mark.setLate(late);
        return mark;
    }
}
