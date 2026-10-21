package com.sis;

import com.sis.dto.AttendanceApprovalRequest;
import com.sis.entity.Attendance;
import com.sis.entity.Course;
import com.sis.entity.Student;
import com.sis.entity.Teacher;
import com.sis.entity.User;
import com.sis.repository.AttendanceRepository;
import com.sis.repository.CourseRepository;
import com.sis.repository.TeacherRepository;
import com.sis.repository.UserRepository;
import com.sis.service.AttendanceService;
import com.sis.service.StudentService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class AttendanceApprovalServiceTest {

    @Test
    void teacherApprovalPersistsPresentAbsentAndLateForEveryEnrolledStudent() {
        AttendanceRepository attendanceRepository = Mockito.mock(AttendanceRepository.class);
        CourseRepository courseRepository = Mockito.mock(CourseRepository.class);
        TeacherRepository teacherRepository = Mockito.mock(TeacherRepository.class);
        UserRepository userRepository = Mockito.mock(UserRepository.class);

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
        course.setTeacher(teacher);
        course.setStudents(Set.of(presentStudent, absentStudent, lateStudent));

        when(userRepository.findByUsernameIgnoreCase("teacher1")).thenReturn(Optional.of(user));
        when(teacherRepository.findByUserId(10L)).thenReturn(Optional.of(teacher));
        when(courseRepository.findById(30L)).thenReturn(Optional.of(course));
        when(attendanceRepository.findByStudentIdAndCourseIdAndDate(any(), any(), any())).thenReturn(List.of());
        when(attendanceRepository.save(any(Attendance.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(attendanceRepository.findByCourseIdAndDateWithStudentAndCourse(
                30L, LocalDate.of(2026, 9, 14))).thenReturn(course.getStudents().stream()
                .map(student -> new Attendance(student, course, LocalDate.of(2026, 9, 14), true))
                .collect(java.util.stream.Collectors.toList()));

        AttendanceService service = new AttendanceService();
        ReflectionTestUtils.setField(service, "attendanceRepository", attendanceRepository);
        ReflectionTestUtils.setField(service, "courseRepository", courseRepository);
        ReflectionTestUtils.setField(service, "teacherRepository", teacherRepository);
        ReflectionTestUtils.setField(service, "userRepository", userRepository);
        ReflectionTestUtils.setField(service, "studentService", Mockito.mock(StudentService.class));

        AttendanceApprovalRequest request = new AttendanceApprovalRequest();
        request.setCourseId(30L);
        request.setDate(LocalDate.of(2026, 9, 14));
        request.setRecords(List.of(mark(1L, true), mark(2L, false), mark(3L, true, true)));

        List<Attendance> saved = service.approveTeacherRosterAttendance("teacher1", request);

        assertEquals(3, saved.size());
        Mockito.verify(attendanceRepository).findByCourseIdAndDateWithStudentAndCourse(
                30L, LocalDate.of(2026, 9, 14));
        ArgumentCaptor<Attendance> captor = ArgumentCaptor.forClass(Attendance.class);
        Mockito.verify(attendanceRepository, Mockito.times(3)).save(captor.capture());
        assertEquals(2, captor.getAllValues().stream().filter(Attendance::isPresent).count());
        assertEquals(1, captor.getAllValues().stream().filter(record -> !record.isPresent()).count());
        assertEquals(1, captor.getAllValues().stream().filter(Attendance::isLate).count());
    }

    @Test
    void teacherApprovalRejectsIncompleteRoster() {
        AttendanceService service = new AttendanceService();
        AttendanceRepository attendanceRepository = Mockito.mock(AttendanceRepository.class);
        CourseRepository courseRepository = Mockito.mock(CourseRepository.class);
        TeacherRepository teacherRepository = Mockito.mock(TeacherRepository.class);
        UserRepository userRepository = Mockito.mock(UserRepository.class);
        User user = new User();
        user.setId(10L);
        Teacher teacher = new Teacher();
        teacher.setId(20L);
        Course course = new Course();
        course.setId(30L);
        course.setTeacher(teacher);
        course.setStudents(Set.of(student(1L, "Alice"), student(2L, "Bob")));
        when(userRepository.findByUsernameIgnoreCase("teacher1")).thenReturn(Optional.of(user));
        when(teacherRepository.findByUserId(10L)).thenReturn(Optional.of(teacher));
        when(courseRepository.findById(30L)).thenReturn(Optional.of(course));
        ReflectionTestUtils.setField(service, "attendanceRepository", attendanceRepository);
        ReflectionTestUtils.setField(service, "courseRepository", courseRepository);
        ReflectionTestUtils.setField(service, "teacherRepository", teacherRepository);
        ReflectionTestUtils.setField(service, "userRepository", userRepository);
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
        User user = new User();
        user.setId(10L);
        Teacher teacher = new Teacher();
        teacher.setId(20L);
        Course course = new Course();
        course.setId(30L);
        course.setTeacher(teacher);
        course.setStudents(Set.of(student(1L, "Alice"), student(2L, "Bob")));
        when(userRepository.findByUsernameIgnoreCase("teacher1")).thenReturn(Optional.of(user));
        when(teacherRepository.findByUserId(10L)).thenReturn(Optional.of(teacher));
        when(courseRepository.findById(30L)).thenReturn(Optional.of(course));
        ReflectionTestUtils.setField(service, "attendanceRepository", attendanceRepository);
        ReflectionTestUtils.setField(service, "courseRepository", courseRepository);
        ReflectionTestUtils.setField(service, "teacherRepository", teacherRepository);
        ReflectionTestUtils.setField(service, "userRepository", userRepository);
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
        User user = new User();
        user.setId(10L);
        Teacher teacher = new Teacher();
        teacher.setId(20L);
        Course course = new Course();
        course.setId(30L);
        course.setTeacher(teacher);
        course.setStudents(Set.of(student(1L, "Alice"), student(2L, "Bob")));
        when(userRepository.findByUsernameIgnoreCase("teacher1")).thenReturn(Optional.of(user));
        when(teacherRepository.findByUserId(10L)).thenReturn(Optional.of(teacher));
        when(courseRepository.findById(30L)).thenReturn(Optional.of(course));
        ReflectionTestUtils.setField(service, "attendanceRepository", attendanceRepository);
        ReflectionTestUtils.setField(service, "courseRepository", courseRepository);
        ReflectionTestUtils.setField(service, "teacherRepository", teacherRepository);
        ReflectionTestUtils.setField(service, "userRepository", userRepository);
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
        User user = new User();
        user.setId(10L);
        Teacher teacher = new Teacher();
        teacher.setId(20L);
        Course course = new Course();
        course.setId(30L);
        course.setTeacher(teacher);
        course.setStudents(Set.of(student(1L, "Alice"), student(2L, "Bob")));
        when(userRepository.findByUsernameIgnoreCase("teacher1")).thenReturn(Optional.of(user));
        when(teacherRepository.findByUserId(10L)).thenReturn(Optional.of(teacher));
        when(courseRepository.findById(30L)).thenReturn(Optional.of(course));
        ReflectionTestUtils.setField(service, "attendanceRepository", attendanceRepository);
        ReflectionTestUtils.setField(service, "courseRepository", courseRepository);
        ReflectionTestUtils.setField(service, "teacherRepository", teacherRepository);
        ReflectionTestUtils.setField(service, "userRepository", userRepository);
        ReflectionTestUtils.setField(service, "studentService", Mockito.mock(StudentService.class));

        AttendanceApprovalRequest request = new AttendanceApprovalRequest();
        request.setCourseId(30L);
        request.setDate(LocalDate.now());
        request.setRecords(List.of(mark(1L, null), mark(2L, true)));

        assertThrows(IllegalArgumentException.class, () -> service.approveTeacherRosterAttendance("teacher1", request));
        Mockito.verifyNoInteractions(attendanceRepository);
    }

    private static Student student(Long id, String name) {
        Student student = new Student();
        student.setId(id);
        student.setFullName(name);
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
