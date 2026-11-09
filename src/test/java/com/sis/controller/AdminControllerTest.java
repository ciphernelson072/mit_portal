package com.sis.controller;

import com.sis.dto.AdminDashboardOverviewDto;
import com.sis.dto.AdminFeeDto;
import com.sis.dto.AdminStatisticsDto;
import com.sis.dto.ChangeStudentStatusRequest;
import com.sis.dto.CreateUserRequest;
import com.sis.dto.CreateTeacherRequest;
import com.sis.dto.AdminTeacherDto;
import com.sis.dto.StudentStatusHistoryDto;
import com.sis.dto.UserDto;
import com.sis.entity.AuditLog;
import com.sis.entity.User;
import com.sis.repository.AuditLogRepository;
import com.sis.entity.StudentLifecycleStatus;
import com.sis.service.AdminService;
import com.sis.service.EmailService;
import com.sis.service.SchoolService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminControllerTest {

    @Test
    void hiringEmailsTemporaryCredentialsWhenDeliveryIsConfigured() {
        AdminController controller = new AdminController();
        AdminService adminService = mock(AdminService.class);
        EmailService emailService = mock(EmailService.class);
        ReflectionTestUtils.setField(controller, "adminService", adminService);
        ReflectionTestUtils.setField(controller, "emailService", emailService);
        CreateTeacherRequest request = new CreateTeacherRequest();
        request.setEmail("teacher@example.edu");
        request.setFullName("Teacher Example");
        request.setUsername("teacher.user");
        request.setPassword("temporary-secret");
        when(emailService.isDeliveryConfigured()).thenReturn(true);
        when(adminService.hireTeacher(request)).thenReturn(new AdminTeacherDto());

        var response = controller.hireTeacher(request);

        verify(emailService).sendHiringNotification("teacher@example.edu", "Teacher Example",
                "teacher.user", "temporary-secret");
        assertEquals(201, response.getStatusCode().value());
    }

    @Test
    void accountCreationIsAuditedWithoutRecordingCredentials() {
        AdminController controller = new AdminController();
        AdminService adminService = mock(AdminService.class);
        SchoolService schoolService = mock(SchoolService.class);
        AuditLogRepository auditLogs = mock(AuditLogRepository.class);
        ReflectionTestUtils.setField(controller, "adminService", adminService);
        ReflectionTestUtils.setField(controller, "schoolService", schoolService);
        ReflectionTestUtils.setField(controller, "auditLogRepository", auditLogs);
        User admin = new User();
        admin.setId(7L);
        when(schoolService.getUserByUsername("admin-user")).thenReturn(java.util.Optional.of(admin));
        when(adminService.createUser(any(CreateUserRequest.class)))
                .thenReturn(new UserDto(22L, "new-student", "student@example.edu", null, "STUDENT", true));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin-user", "n/a",
                        AuthorityUtils.createAuthorityList("ADMIN")));

        try {
            controller.createUser(new CreateUserRequest());

            var auditCaptor = forClass(AuditLog.class);
            verify(auditLogs).save(auditCaptor.capture());
            assertEquals(7L, auditCaptor.getValue().getUserId());
            assertEquals("ADMIN_USER_CREATED", auditCaptor.getValue().getAction());
            assertTrue(auditCaptor.getValue().getDetails().contains("Target account #22"));
            assertFalse(auditCaptor.getValue().getDetails().contains("password"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void gradeImportIsAuditedWithAuthenticatedAdminActor() {
        AdminController controller = new AdminController();
        AdminService adminService = mock(AdminService.class);
        SchoolService schoolService = mock(SchoolService.class);
        AuditLogRepository auditLogs = mock(AuditLogRepository.class);
        ReflectionTestUtils.setField(controller, "adminService", adminService);
        ReflectionTestUtils.setField(controller, "schoolService", schoolService);
        ReflectionTestUtils.setField(controller, "auditLogRepository", auditLogs);

        User admin = new User();
        admin.setId(17L);
        when(schoolService.getUserByUsername("audit-admin")).thenReturn(java.util.Optional.of(admin));
        com.sis.entity.Student student = new com.sis.entity.Student();
        student.setId(24L);
        User studentUser = new User();
        studentUser.setUsername("student-24");
        student.setUser(studentUser);
        com.sis.entity.Course course = new com.sis.entity.Course();
        course.setId(31L);
        course.setName("Accounting");
        com.sis.entity.Grade grade = new com.sis.entity.Grade(student, course, "A", "Reviewed");
        grade.setId(45L);
        when(schoolService.getStudentById(24L)).thenReturn(java.util.Optional.of(student));
        when(schoolService.getCourseById(31L)).thenReturn(java.util.Optional.of(course));
        when(schoolService.studentEnrolledInCourse(24L, 31L)).thenReturn(true);
        when(schoolService.saveGrade(student, course, "A", "Reviewed")).thenReturn(grade);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("audit-admin", "n/a",
                        AuthorityUtils.createAuthorityList("ADMIN")));

        try {
            com.sis.dto.AdminGradeImportRequest request = new com.sis.dto.AdminGradeImportRequest();
            request.setStudentId(24L);
            request.setCourseId(31L);
            request.setGradeValue("A");
            request.setRemarks("Reviewed");
            controller.importGrade(request);

            var auditCaptor = forClass(AuditLog.class);
            verify(auditLogs).save(auditCaptor.capture());
            assertEquals(17L, auditCaptor.getValue().getUserId());
            assertEquals("GRADE_IMPORTED", auditCaptor.getValue().getAction());
            assertTrue(auditCaptor.getValue().getDetails().contains("Grade 45"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void dashboardOverviewReturnsAggregatedSummary() {
        AdminController controller = new AdminController();
        AdminService adminService = mock(AdminService.class);
        SchoolService schoolService = mock(SchoolService.class);

        ReflectionTestUtils.setField(controller, "adminService", adminService);
        ReflectionTestUtils.setField(controller, "schoolService", schoolService);

        when(adminService.getStatistics()).thenReturn(new AdminStatisticsDto(25L, 9L, 34L, 2L));
        AdminFeeDto settled = new AdminFeeDto(1L, 10L, "Ada", "ada", "Tuition",
                new BigDecimal("500000"), true, LocalDate.now(), LocalDate.now());
        settled.setSettlementAmounts(new BigDecimal("500000"), BigDecimal.ZERO);
        AdminFeeDto partial = new AdminFeeDto(2L, 11L, "Ben", "ben", "Lab",
                new BigDecimal("200000"), false, LocalDate.now(), null);
        partial.setSettlementAmounts(new BigDecimal("50000"), new BigDecimal("150000"));
        when(schoolService.getAllFees()).thenReturn(List.of(settled, partial));
        when(schoolService.listCourseDtos()).thenReturn(List.of(
                new com.sis.dto.AdminCourseDto(1L, "Math", "MTH101", "Science", "Dr. Doe", "2025/2026", 3),
                new com.sis.dto.AdminCourseDto(2L, "Physics", "PHY101", "Science", "Dr. Smith", "2025/2026", 3)
        ));

        ResponseEntity<AdminDashboardOverviewDto> response = controller.getDashboardOverview();

        assertEquals(200, response.getStatusCode().value());
        AdminDashboardOverviewDto body = response.getBody();
        assertEquals(25L, body.getTotalStudents());
        assertEquals(9L, body.getTotalTeachers());
        assertEquals(34L, body.getTotalUsers());
        assertEquals(2L, body.getTotalCourses());
        assertEquals(2L, body.getTotalFees());
        assertEquals(1L, body.getPaidFeesCount());
        assertEquals(1L, body.getUnpaidFeesCount());
        assertEquals(new BigDecimal("550000"), body.getTotalRevenue());
    }

    @Test
    void lifecycleStatusEndpointUsesAuthenticatedAdminAndSpecificEnrollment() {
        AdminController controller = new AdminController();
        AdminService adminService = mock(AdminService.class);
        ReflectionTestUtils.setField(controller, "adminService", adminService);
        ChangeStudentStatusRequest request = new ChangeStudentStatusRequest();
        request.setProgramId(7L);
        request.setAcademicYearId(2026L);
        request.setStatus(StudentLifecycleStatus.SUSPENDED);
        request.setReason("Pending review");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin-user", "n/a",
                        AuthorityUtils.createAuthorityList("ADMIN")));

        try {
            ResponseEntity<String> response = controller.changeStudentStatus(42L, request);

            assertEquals(200, response.getStatusCode().value());
            verify(adminService).changeStudentLifecycleStatus(42L, 7L, 2026L,
                    StudentLifecycleStatus.SUSPENDED, "Pending review", "admin-user");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void statusHistoryEndpointReturnsAdminServiceAuditEntries() {
        AdminController controller = new AdminController();
        AdminService adminService = mock(AdminService.class);
        ReflectionTestUtils.setField(controller, "adminService", adminService);
        List<StudentStatusHistoryDto> history = List.of(new StudentStatusHistoryDto(
                9L, "TRANSFERRED", "Approved programme change", "admin",
                LocalDateTime.parse("2026-10-19T09:30:00"), 14L, "Computer Science", "2026/2027"));
        when(adminService.getStudentStatusHistory(42L)).thenReturn(history);

        ResponseEntity<List<StudentStatusHistoryDto>> response = controller.getStudentStatusHistory(42L);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(history, response.getBody());
    }
}
