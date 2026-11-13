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
import com.sis.entity.CourseAssessmentComponent;
import com.sis.entity.AssessmentComponentType;
import com.sis.entity.Grade;
import com.sis.entity.GradeAssessmentMark;
import com.sis.entity.GradeStatus;
import com.sis.service.NotificationService;
import com.sis.entity.Student;
import com.sis.entity.User;
import com.sis.repository.AuditLogRepository;
import com.sis.repository.AttendanceRepository;
import com.sis.repository.GradeRepository;
import com.sis.repository.CourseAssessmentComponentRepository;
import com.sis.repository.GradeAssessmentMarkRepository;
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
    void approvedGradesCannotBeResubmittedByTeacher() {
        GradeRepository gradeRepository = Mockito.mock(GradeRepository.class);
        StudentRepository studentRepository = Mockito.mock(StudentRepository.class);
        com.sis.service.StudentService studentService = Mockito.mock(com.sis.service.StudentService.class);
        AuditLogRepository auditLogs = Mockito.mock(AuditLogRepository.class);
        UserRepository users = Mockito.mock(UserRepository.class);
        GradeApprovalService service = new GradeApprovalService(gradeRepository, studentRepository,
                studentService, auditLogs, users, Mockito.mock(NotificationService.class));
        Grade approved = new Grade();
        approved.setStatus(GradeStatus.APPROVED);

        assertThrows(IllegalStateException.class, () -> service.submitForApproval(approved, "teacher"));
        Mockito.verifyNoInteractions(gradeRepository, auditLogs);
    }

        @Test
        void gradeReviewIsAuditedAndApprovalRequiresTheReviewedResultState() {
        GradeRepository gradeRepository = Mockito.mock(GradeRepository.class);
        StudentRepository studentRepository = Mockito.mock(StudentRepository.class);
        com.sis.service.StudentService studentService = Mockito.mock(com.sis.service.StudentService.class);
        AuditLogRepository auditLogs = Mockito.mock(AuditLogRepository.class);
        UserRepository users = Mockito.mock(UserRepository.class);
        NotificationService notifications = Mockito.mock(NotificationService.class);
        CourseAssessmentComponentRepository components = Mockito.mock(CourseAssessmentComponentRepository.class);
        GradeAssessmentMarkRepository assessmentMarks = Mockito.mock(GradeAssessmentMarkRepository.class);
        com.sis.service.AssessmentCalculationService calculations =
                new com.sis.service.AssessmentCalculationService();
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
        course.setId(7L);
        course.setName("Database Systems");
        course.setCredits(3);
        CourseAssessmentComponent component = new CourseAssessmentComponent();
        component.setId(17L);
        component.setCourse(course);
        component.setComponentCode("TOTAL");
        component.setDisplayName("Course total");
        component.setComponentType(AssessmentComponentType.COURSEWORK);
        component.setWeightPercentage(new BigDecimal("100"));
        component.setMaxScore(new BigDecimal("100"));
        component.setRequired(true);
        Mockito.when(components.findByCourseIdOrderByIdAsc(7L)).thenReturn(List.of(component));
        Grade submitted = new Grade();
        submitted.setId(51L);
        submitted.setStudent(student);
        submitted.setCourse(course);
        submitted.setGradeValue("A");
        submitted.setGpaScore(new BigDecimal("5.0"));
        submitted.setGradingPolicyVersion("UG_5_POINT_V1");
        submitted.setTotalMark(new BigDecimal("80.00"));
        submitted.setRetakeRequired(false);
        submitted.setStatus(GradeStatus.PENDING);
        Grade toApprove = new Grade();
        toApprove.setId(52L);
        toApprove.setStudent(student);
        toApprove.setCourse(course);
        toApprove.setGradeValue("A");
        toApprove.setGpaScore(new BigDecimal("5.0"));
        toApprove.setGradingPolicyVersion("UG_5_POINT_V1");
        toApprove.setRetakeRequired(false);
        toApprove.setStatus(GradeStatus.PENDING);
        toApprove.setTotalMark(new BigDecimal("80.00"));
        GradeAssessmentMark submittedMark = new GradeAssessmentMark();
        submittedMark.setGrade(submitted);
        submittedMark.setComponent(component);
        submittedMark.setScore(new BigDecimal("80"));
        GradeAssessmentMark approvedMark = new GradeAssessmentMark();
        approvedMark.setGrade(toApprove);
        approvedMark.setComponent(component);
        approvedMark.setScore(new BigDecimal("80"));
        Mockito.when(assessmentMarks.findByGradeId(51L)).thenReturn(List.of(submittedMark));
        Mockito.when(assessmentMarks.findByGradeId(52L)).thenReturn(List.of(approvedMark));
        Grade toReject = new Grade();
        toReject.setId(53L);
        toReject.setStudent(student);
        toReject.setStatus(GradeStatus.PENDING);
        Mockito.when(gradeRepository.findByIdForUpdate(52L)).thenReturn(java.util.Optional.of(toApprove));
        Mockito.when(gradeRepository.findByIdForUpdate(53L)).thenReturn(java.util.Optional.of(toReject));
        Mockito.when(gradeRepository.findByIdForUpdate(51L)).thenReturn(java.util.Optional.of(submitted));
        Mockito.when(gradeRepository.findByIdWithStudentUserAndCourse(Mockito.anyLong()))
            .thenAnswer(invocation -> {
                Long id = invocation.getArgument(0);
                if (id.equals(51L)) return java.util.Optional.of(submitted);
                if (id.equals(52L)) return java.util.Optional.of(toApprove);
                if (id.equals(53L)) return java.util.Optional.of(toReject);
                return java.util.Optional.empty();
            });

        GradeApprovalService service = new GradeApprovalService(gradeRepository, studentRepository,
            studentService, auditLogs, users, notifications,
            Mockito.mock(com.sis.service.AcademicResultCorrectionService.class),
            components, assessmentMarks, calculations);
        service.submitForApproval(submitted, "teacher1");
        service.acknowledgeAssessmentReview(52L, "admin");
        service.approve(52L, "admin");
        service.reject(53L, "admin", "Missing assessment evidence");

        org.mockito.ArgumentCaptor<AuditLog> auditCaptor = org.mockito.ArgumentCaptor.forClass(AuditLog.class);
        Mockito.verify(auditLogs, Mockito.times(4)).save(auditCaptor.capture());
        List<AuditLog> events = auditCaptor.getAllValues();
        assertEquals(List.of("GRADE_SUBMITTED_FOR_APPROVAL", "GRADE_ASSESSMENT_REVIEWED",
                "GRADE_APPROVED", "GRADE_REJECTED"),
            events.stream().map(AuditLog::getAction).collect(java.util.stream.Collectors.toList()));
        assertTrue(events.stream().allMatch(event -> Long.valueOf(31L).equals(event.getUserId())));
        assertTrue(events.get(0).getDetails().contains("Grade #51"));
        assertTrue(events.get(1).getDetails().contains("grade #52"));
        assertTrue(events.get(2).getDetails().contains("Grade #52"));
        assertTrue(events.get(3).getDetails().contains("Grade #53"));
        Mockito.verify(notifications, Mockito.times(1)).create(
                Mockito.anyLong(), Mockito.anyString(), Mockito.anyString(),
                Mockito.eq(com.sis.entity.NotificationCategory.GRADE));

        Grade changedAfterReview = new Grade();
        changedAfterReview.setId(54L);
        changedAfterReview.setStudent(student);
        changedAfterReview.setCourse(course);
        changedAfterReview.setGradeValue("A");
        changedAfterReview.setGpaScore(new BigDecimal("5.0"));
        changedAfterReview.setGradingPolicyVersion("UG_5_POINT_V1");
        changedAfterReview.setTotalMark(new BigDecimal("80.00"));
        changedAfterReview.setRetakeRequired(false);
        changedAfterReview.setStatus(GradeStatus.PENDING);
        GradeAssessmentMark unchangedMark = new GradeAssessmentMark();
        unchangedMark.setGrade(changedAfterReview);
        unchangedMark.setComponent(component);
        unchangedMark.setScore(new BigDecimal("80"));
        Mockito.when(gradeRepository.findByIdForUpdate(54L))
                .thenReturn(java.util.Optional.of(changedAfterReview));
        Mockito.when(assessmentMarks.findByGradeId(54L)).thenReturn(List.of(unchangedMark));

        service.acknowledgeAssessmentReview(54L, "admin");
        changedAfterReview.setRemarks("Changed after reviewer acknowledgment");
        assertThrows(IllegalStateException.class, () -> service.approve(54L, "admin"));
        }

    @Test
    void mfaSetupResponseExposesSecretAndOtpUri() {
        MfaSetupResponse response = new MfaSetupResponse("JBSWY3DPEHPK3PXP", "otpauth://totp/MIT%20Portal:user@example.com?secret=JBSWY3DPEHPK3PXP&issuer=MIT%20Portal");

        assertNotNull(response);
        assertEquals("JBSWY3DPEHPK3PXP", response.getSecret());
        assertEquals("otpauth://totp/MIT%20Portal:user@example.com?secret=JBSWY3DPEHPK3PXP&issuer=MIT%20Portal", response.getOtpUri());
    }
}
