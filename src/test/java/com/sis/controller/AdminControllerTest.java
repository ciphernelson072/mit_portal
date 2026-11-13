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
import com.sis.service.GradeImportService;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
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
        request.setEmail(" Teacher@Example.EDU ");
        request.setFullName("Teacher Example");
        request.setUsername("Teacher.User");
        request.setPassword("temporary-secret");
        when(emailService.isDeliveryConfigured()).thenReturn(true);
        AdminTeacherDto hired = new AdminTeacherDto();
        hired.setEmail("teacher@example.edu");
        hired.setUsername("teacher.user");
        when(adminService.hireTeacher(request)).thenReturn(hired);

        var response = controller.hireTeacher(request);

        verify(emailService).sendHiringNotification("teacher@example.edu", "Teacher Example",
                "teacher.user", "temporary-secret");
        assertEquals(201, response.getStatusCode().value());
        assertTrue(Boolean.TRUE.equals(response.getBody().getEmailSent()));
        assertTrue(response.getBody().getEmailDeliveryMessage().contains("sent"));
    }

    @Test
    void reportsWhenHiringEmailIsNotConfiguredWithoutFailingTeacherCreation() {
        AdminController controller = new AdminController();
        AdminService adminService = mock(AdminService.class);
        EmailService emailService = mock(EmailService.class);
        ReflectionTestUtils.setField(controller, "adminService", adminService);
        ReflectionTestUtils.setField(controller, "emailService", emailService);
        CreateTeacherRequest request = new CreateTeacherRequest();
        request.setEmail("teacher@example.edu");
        request.setFullName("Teacher Example");
        request.setUsername("Teacher.User");
        when(emailService.isDeliveryConfigured()).thenReturn(false);
        AdminTeacherDto hired = new AdminTeacherDto();
        hired.setEmail("teacher@example.edu");
        hired.setUsername("teacher.user");
        when(adminService.hireTeacher(request)).thenReturn(hired);

        var response = controller.hireTeacher(request);

        assertEquals(201, response.getStatusCode().value());
        assertFalse(Boolean.TRUE.equals(response.getBody().getEmailSent()));
        assertTrue(response.getBody().getEmailDeliveryMessage().contains("not configured"));
        verify(emailService, never()).sendHiringNotification(any(), any(), any(), any());
    }

    @Test
    void reportsHiringEmailProviderFailureWithoutSuggestingDuplicateHire() {
        AdminController controller = new AdminController();
        AdminService adminService = mock(AdminService.class);
        EmailService emailService = mock(EmailService.class);
        ReflectionTestUtils.setField(controller, "adminService", adminService);
        ReflectionTestUtils.setField(controller, "emailService", emailService);
        CreateTeacherRequest request = new CreateTeacherRequest();
        request.setEmail("teacher@example.edu");
        request.setFullName("Teacher Example");
        request.setUsername("Teacher.User");
        when(emailService.isDeliveryConfigured()).thenReturn(true);
        AdminTeacherDto hired = new AdminTeacherDto();
        hired.setEmail("teacher@example.edu");
        hired.setUsername("teacher.user");
        when(adminService.hireTeacher(request)).thenReturn(hired);
        doThrow(new IllegalStateException("provider unavailable")).when(emailService)
                .sendHiringNotification("teacher@example.edu", "Teacher Example", "teacher.user", null);

        var response = controller.hireTeacher(request);

        assertEquals(201, response.getStatusCode().value());
        assertFalse(Boolean.TRUE.equals(response.getBody().getEmailSent()));
        assertTrue(response.getBody().getEmailDeliveryMessage().contains("do not submit the hire request again"));
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
    void gradeImportDelegatesToValidatedServiceWithAuthenticatedAdminActor() {
        AdminController controller = new AdminController();
        GradeImportService gradeImportService = mock(GradeImportService.class);
        ReflectionTestUtils.setField(controller, "gradeImportService", gradeImportService);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("audit-admin", "n/a",
                        AuthorityUtils.createAuthorityList("ADMIN")));

        try {
            com.sis.dto.AdminGradeImportRequest request = new com.sis.dto.AdminGradeImportRequest();
            request.setStudentId(24L);
            request.setCourseId(31L);
            request.setGradeValue("A");
            request.setRemarks("Reviewed");
            var response = controller.importGrade(request);

            verify(gradeImportService).importGrade(request, "audit-admin");
            assertEquals(201, response.getStatusCode().value());
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
