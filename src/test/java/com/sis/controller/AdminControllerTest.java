package com.sis.controller;

import com.sis.dto.AdminDashboardOverviewDto;
import com.sis.dto.AdminFeeDto;
import com.sis.dto.AdminStatisticsDto;
import com.sis.dto.ChangeStudentStatusRequest;
import com.sis.dto.StudentStatusHistoryDto;
import com.sis.entity.StudentLifecycleStatus;
import com.sis.service.AdminService;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminControllerTest {

    @Test
    void dashboardOverviewReturnsAggregatedSummary() {
        AdminController controller = new AdminController();
        AdminService adminService = mock(AdminService.class);
        SchoolService schoolService = mock(SchoolService.class);

        ReflectionTestUtils.setField(controller, "adminService", adminService);
        ReflectionTestUtils.setField(controller, "schoolService", schoolService);

        when(adminService.getStatistics()).thenReturn(new AdminStatisticsDto(25L, 9L, 34L, 2L));
        when(schoolService.getAllFees()).thenReturn(List.of(
                new AdminFeeDto(1L, 10L, "Ada", "ada", "Tuition", new BigDecimal("500000"), true, LocalDate.now(), LocalDate.now()),
                new AdminFeeDto(2L, 11L, "Ben", "ben", "Lab", new BigDecimal("200000"), false, LocalDate.now(), null)
        ));
        when(schoolService.listCourseDtos()).thenReturn(List.of(
                new com.sis.dto.AdminCourseDto(1L, "Math", "MTH101", "Science", "Dr. Doe", "2025/2026", 3),
                new com.sis.dto.AdminCourseDto(2L, "Physics", "PHY101", "Science", "Dr. Smith", "2025/2026", 3)
        ));

        ResponseEntity<AdminDashboardOverviewDto> response = controller.getDashboardOverview();

        assertEquals(200, response.getStatusCodeValue());
        AdminDashboardOverviewDto body = response.getBody();
        assertEquals(25L, body.getTotalStudents());
        assertEquals(9L, body.getTotalTeachers());
        assertEquals(34L, body.getTotalUsers());
        assertEquals(2L, body.getTotalCourses());
        assertEquals(2L, body.getTotalFees());
        assertEquals(1L, body.getPaidFeesCount());
        assertEquals(1L, body.getUnpaidFeesCount());
        assertEquals(new BigDecimal("500000"), body.getTotalRevenue());
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

            assertEquals(200, response.getStatusCodeValue());
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

        assertEquals(200, response.getStatusCodeValue());
        assertEquals(history, response.getBody());
    }
}
