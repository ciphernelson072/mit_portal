package com.sis;

import com.sis.controller.GradeApprovalController;
import com.sis.dto.AdminGradeDto;
import com.sis.dto.AdminStudentDto;
import com.sis.dto.AdminTeacherDto;
import com.sis.dto.MfaSetupResponse;
import com.sis.dto.GradeDto;
import com.sis.dto.StudentProfileDto;
import com.sis.dto.TeacherProfileDto;
import com.sis.entity.AuditLog;
import com.sis.entity.Course;
import com.sis.entity.Grade;
import com.sis.entity.GradeStatus;
import com.sis.service.NotificationService;
import com.sis.entity.Student;
import com.sis.entity.User;
import com.sis.repository.AuditLogRepository;
import com.sis.repository.AttendanceRepository;
import com.sis.repository.GradeRepository;
import com.sis.repository.StudentRepository;
import com.sis.repository.UserRepository;
import com.sis.service.AcademicRecordService;
import com.sis.service.FeeService;
import com.sis.service.GradeApprovalService;
import com.sis.service.SchoolService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

class GradeApprovalWorkflowTest {

    @Test
    void gradeDtoSurfacesApprovalStateAndCourseMetadata() {
        GradeRepository gradeRepository = Mockito.mock(GradeRepository.class);
        AttendanceRepository attendanceRepository = Mockito.mock(AttendanceRepository.class);
        FeeService feeService = Mockito.mock(FeeService.class);
        StudentRepository studentRepository = Mockito.mock(StudentRepository.class);

        AcademicRecordService service = new AcademicRecordService(gradeRepository, attendanceRepository, feeService, studentRepository);

        Course course = new Course();
        course.setName("Database Systems");
        course.setCredits(3);
        course.setSemester("Semester 4");

        Grade grade = new Grade();
        grade.setCourse(course);
        grade.setGradeValue("A");
        grade.setGpaScore(BigDecimal.valueOf(4.0));
        grade.setRemarks("Excellent work");
        grade.setStatus(GradeStatus.PENDING);
        grade.setSubmittedBy("teacher1");
        grade.setApprovedBy("admin");
        grade.setRejectionReason("Needs review");

        GradeDto dto = service.toGradeDto(grade);

        assertEquals(3, dto.getCredits());
        assertEquals("Semester 4", dto.getSemester());
        assertEquals("PENDING", dto.getStatus());
        assertEquals("teacher1", dto.getSubmittedBy());
        assertEquals("admin", dto.getApprovedBy());
        assertEquals("Needs review", dto.getRejectionReason());
    }

    @Test
    void profileDtosAndAdminDtosExposeProfileImagePaths() {
        StudentProfileDto studentProfile = new StudentProfileDto();
        studentProfile.setProfileImagePath("students/1_abc.png");
        assertEquals("students/1_abc.png", studentProfile.getProfileImagePath());

        TeacherProfileDto teacherProfile = new TeacherProfileDto();
        teacherProfile.setProfileImagePath("teachers/2_def.png");
        assertEquals("teachers/2_def.png", teacherProfile.getProfileImagePath());

        AdminStudentDto adminStudent = new AdminStudentDto();
        adminStudent.setProfileImagePath("students/1_abc.png");
        assertEquals("students/1_abc.png", adminStudent.getProfileImagePath());

        AdminTeacherDto adminTeacher = new AdminTeacherDto();
        adminTeacher.setProfileImagePath("teachers/2_def.png");
        assertEquals("teachers/2_def.png", adminTeacher.getProfileImagePath());
    }

    @Test
    void pendingAdminQueueReturnsAdminGradeMetadataShape() {
        GradeRepository gradeRepository = Mockito.mock(GradeRepository.class);
        GradeApprovalService gradeApprovalService = Mockito.mock(GradeApprovalService.class);
        SchoolService schoolService = Mockito.mock(SchoolService.class);

        GradeApprovalController controller = new GradeApprovalController(gradeApprovalService, schoolService, gradeRepository);

        ResponseEntity<List<AdminGradeDto>> response = controller.pendingGrades();

        assertEquals(200, response.getStatusCodeValue());
    }

    @Test
    void studentGradeQueriesOnlyApprovedGrades() {
        GradeRepository gradeRepository = Mockito.mock(GradeRepository.class);
        AttendanceRepository attendanceRepository = Mockito.mock(AttendanceRepository.class);
        FeeService feeService = Mockito.mock(FeeService.class);
        StudentRepository studentRepository = Mockito.mock(StudentRepository.class);
        AcademicRecordService service = new AcademicRecordService(gradeRepository, attendanceRepository, feeService, studentRepository);

        service.getGradesForStudent(42L);

        verify(gradeRepository).findByStudentIdAndStatusOrderByApprovedAtDescIdDesc(42L, GradeStatus.APPROVED);
    }

    @Test
    void nonPendingGradesCannotBeApprovedOrRejected() {
        GradeRepository gradeRepository = Mockito.mock(GradeRepository.class);
        StudentRepository studentRepository = Mockito.mock(StudentRepository.class);
        com.sis.service.StudentService studentService = Mockito.mock(com.sis.service.StudentService.class);
        AuditLogRepository auditLogs = Mockito.mock(AuditLogRepository.class);
        UserRepository users = Mockito.mock(UserRepository.class);

        Grade grade = new Grade();
        grade.setStatus(GradeStatus.APPROVED);
        grade.setStudent(new com.sis.entity.Student());
        Mockito.when(gradeRepository.findByIdForUpdate(99L)).thenReturn(java.util.Optional.of(grade));

        GradeApprovalService service = new GradeApprovalService(gradeRepository, studentRepository,
            studentService, auditLogs, users, Mockito.mock(NotificationService.class));

        assertThrows(IllegalStateException.class, () -> service.approve(99L, "admin"));
        assertThrows(IllegalStateException.class, () -> service.reject(99L, "admin", "bad reason"));
        Mockito.verifyNoInteractions(auditLogs);
    }

        @Test
        void gradeSubmissionApprovalAndRejectionAreAudited() {
        GradeRepository gradeRepository = Mockito.mock(GradeRepository.class);
        StudentRepository studentRepository = Mockito.mock(StudentRepository.class);
        com.sis.service.StudentService studentService = Mockito.mock(com.sis.service.StudentService.class);
        AuditLogRepository auditLogs = Mockito.mock(AuditLogRepository.class);
        UserRepository users = Mockito.mock(UserRepository.class);
        NotificationService notifications = Mockito.mock(NotificationService.class);
        User actor = new User();
        actor.setId(31L);
        Mockito.when(users.findByUsernameNormalized(Mockito.anyString())).thenReturn(java.util.Optional.of(actor));
        Mockito.when(gradeRepository.save(Mockito.any(Grade.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        Student student = new Student();
        User studentUser = new User();
        studentUser.setId(42L);
        student.setId(14L);
        student.setUser(studentUser);
        Course course = new Course();
        course.setName("Database Systems");
        Grade submitted = new Grade();
        submitted.setId(51L);
        submitted.setStudent(student);
        Grade toApprove = new Grade();
        toApprove.setId(52L);
        toApprove.setStudent(student);
        toApprove.setCourse(course);
        toApprove.setStatus(GradeStatus.PENDING);
        Grade toReject = new Grade();
        toReject.setId(53L);
        toReject.setStudent(student);
        toReject.setStatus(GradeStatus.PENDING);
        Mockito.when(gradeRepository.findByIdForUpdate(52L)).thenReturn(java.util.Optional.of(toApprove));
        Mockito.when(gradeRepository.findByIdForUpdate(53L)).thenReturn(java.util.Optional.of(toReject));
        Mockito.when(gradeRepository.findByIdWithStudentUserAndCourse(Mockito.anyLong()))
            .thenAnswer(invocation -> {
                Long id = invocation.getArgument(0);
                if (id.equals(51L)) return java.util.Optional.of(submitted);
                if (id.equals(52L)) return java.util.Optional.of(toApprove);
                if (id.equals(53L)) return java.util.Optional.of(toReject);
                return java.util.Optional.empty();
            });

        GradeApprovalService service = new GradeApprovalService(gradeRepository, studentRepository,
            studentService, auditLogs, users, notifications);
        service.submitForApproval(submitted, "teacher1");
        service.approve(52L, "admin");
        service.reject(53L, "admin", "Missing assessment evidence");

        org.mockito.ArgumentCaptor<AuditLog> auditCaptor = org.mockito.ArgumentCaptor.forClass(AuditLog.class);
        Mockito.verify(auditLogs, Mockito.times(3)).save(auditCaptor.capture());
        List<AuditLog> events = auditCaptor.getAllValues();
        assertEquals(List.of("GRADE_SUBMITTED_FOR_APPROVAL", "GRADE_APPROVED", "GRADE_REJECTED"),
            events.stream().map(AuditLog::getAction).collect(java.util.stream.Collectors.toList()));
        assertTrue(events.stream().allMatch(event -> Long.valueOf(31L).equals(event.getUserId())));
        assertTrue(events.get(0).getDetails().contains("Grade #51"));
        assertTrue(events.get(1).getDetails().contains("Grade #52"));
        assertTrue(events.get(2).getDetails().contains("Grade #53"));
        Mockito.verify(notifications).create(42L, "Grade available",
            "Your grade for Database Systems is now available.", com.sis.entity.NotificationCategory.GRADE);
        }

    @Test
    void mfaSetupResponseExposesSecretAndOtpUri() {
        MfaSetupResponse response = new MfaSetupResponse("JBSWY3DPEHPK3PXP", "otpauth://totp/MIT%20Portal:user@example.com?secret=JBSWY3DPEHPK3PXP&issuer=MIT%20Portal");

        assertNotNull(response);
        assertEquals("JBSWY3DPEHPK3PXP", response.getSecret());
        assertEquals("otpauth://totp/MIT%20Portal:user@example.com?secret=JBSWY3DPEHPK3PXP&issuer=MIT%20Portal", response.getOtpUri());
    }
}
