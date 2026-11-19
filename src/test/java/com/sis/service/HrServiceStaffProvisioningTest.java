package com.sis.service;

import com.sis.dto.CreateStaffRequest;
import com.sis.dto.CreateLeaveRequest;
import com.sis.dto.ReviewLeaveRequest;
import com.sis.entity.LeaveRequest;
import com.sis.entity.LeaveRequestStatus;
import com.sis.entity.Role;
import com.sis.entity.Staff;
import com.sis.entity.User;
import com.sis.repository.AuditLogRepository;
import com.sis.repository.LeaveRequestRepository;
import com.sis.repository.StaffRepository;
import com.sis.repository.UserRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class HrServiceStaffProvisioningTest {
    private StaffRepository staff;
    private LeaveRequestRepository leaves;
    private UserRepository users;
    private NotificationService notifications;
    private NotificationRecipientService notificationRecipients;
    private HrService service;

    @BeforeEach
    void setUp() {
        staff = mock(StaffRepository.class);
        leaves = mock(LeaveRequestRepository.class);
        users = mock(UserRepository.class);
        notifications = mock(NotificationService.class);
        notificationRecipients = mock(NotificationRecipientService.class);
        service = new HrService(staff, leaves, users, mock(AuditLogRepository.class),
                notifications, notificationRecipients);
    }

    @Test
    void createsHrProfileForActiveTeacherAccount() {
        User user = user(Role.TEACHER, true);
        when(users.findById(7L)).thenReturn(Optional.of(user));
        when(staff.save(any(Staff.class))).thenAnswer(invocation -> invocation.getArgument(0));
        CreateStaffRequest request = request();

        Staff result = service.createStaff(request);

        assertEquals(user, result.getUser());
        assertEquals("EMP-7", result.getEmployeeNumber());
        assertEquals("A. Teacher", result.getFullName());
        verify(staff).save(any(Staff.class));
    }

    @Test
    void rejectsInactiveAccountsAndStudentAccounts() {
        User inactive = user(Role.TEACHER, false);
        when(users.findById(7L)).thenReturn(Optional.of(inactive));
        assertThrows(IllegalArgumentException.class, () -> service.createStaff(request()));

        User student = user(Role.STUDENT, true);
        when(users.findById(7L)).thenReturn(Optional.of(student));
        assertThrows(IllegalArgumentException.class, () -> service.createStaff(request()));
        verify(staff, never()).save(any(Staff.class));
    }

    @Test
    void rejectsDuplicateUserAndEmployeeNumber() {
        User user = user(Role.ADMIN, true);
        when(users.findById(7L)).thenReturn(Optional.of(user));
        when(staff.existsByUser_Id(7L)).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> service.createStaff(request()));

        when(staff.existsByUser_Id(7L)).thenReturn(false);
        when(staff.existsByEmployeeNumber("EMP-7")).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> service.createStaff(request()));
        verify(staff, never()).save(any(Staff.class));
    }

    @Test
    void leaveSubmissionNotifiesActiveAdministratorsExceptTheRequester() {
        User employeeUser = user(Role.TEACHER, true);
        User administrator = user(Role.ADMIN, true);
        administrator.setId(8L);
        Staff employee = new Staff();
        employee.setId(3L);
        employee.setUser(employeeUser);
        employee.setFullName("A. Teacher");
        employee.setActive(true);
        LeaveRequest[] savedLeave = new LeaveRequest[1];
        when(staff.findByUserUsername("employee")).thenReturn(Optional.of(employee));
        when(leaves.findByStaffIdAndStatus(3L, LeaveRequestStatus.PENDING)).thenReturn(List.of());
        when(leaves.save(any(LeaveRequest.class))).thenAnswer(invocation -> {
            LeaveRequest leave = invocation.getArgument(0);
            leave.setId(22L);
            savedLeave[0] = leave;
            return leave;
        });
        when(leaves.findByIdWithStaffUser(22L)).thenAnswer(invocation -> Optional.of(savedLeave[0]));
        when(notificationRecipients.findActiveUsersWithPermission("HR_MANAGE", Role.ADMIN))
                .thenReturn(List.of(employeeUser, administrator));
        CreateLeaveRequest request = new CreateLeaveRequest();
        request.setStartDate(LocalDate.of(2026, 12, 1));
        request.setEndDate(LocalDate.of(2026, 12, 2));
        request.setReason("Annual leave");

        service.requestLeave("employee", request);

        verify(notifications).create(8L, "Staff leave request submitted",
                "A. Teacher requested leave from 2026-12-01 through 2026-12-02.");
        verify(notifications, never()).create(7L, "Staff leave request submitted",
                "A. Teacher requested leave from 2026-12-01 through 2026-12-02.");
    }

    @Test
    void leaveReviewNotifiesTheRequesterAndRecordsTheDecision() {
        User employeeUser = user(Role.TEACHER, true);
        Staff employee = new Staff();
        employee.setUser(employeeUser);
        employee.setFullName("A. Teacher");
        LeaveRequest leave = new LeaveRequest();
        leave.setId(31L);
        leave.setStaff(employee);
        leave.setStartDate(LocalDate.of(2026, 12, 1));
        leave.setEndDate(LocalDate.of(2026, 12, 2));
        leave.setStatus(LeaveRequestStatus.PENDING);
        when(leaves.findByIdWithStaffUser(31L)).thenReturn(Optional.of(leave));
        when(leaves.save(any(LeaveRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        User reviewer = user(Role.ADMIN, true);
        reviewer.setUsername("reviewer");
        reviewer.setId(9L);
        when(users.findByUsernameNormalized("reviewer")).thenReturn(Optional.of(reviewer));
        ReviewLeaveRequest request = new ReviewLeaveRequest();
        request.setStatus(LeaveRequestStatus.REJECTED);
        AuditLogRepository auditLogs = mock(AuditLogRepository.class);
        service = new HrService(staff, leaves, users, auditLogs, notifications, notificationRecipients);

        LeaveRequest result = service.reviewLeave(31L, request, "reviewer");

        Assertions.assertEquals(LeaveRequestStatus.REJECTED, result.getStatus());
        verify(notifications).create(7L, "Staff leave request updated",
                "Your leave request from 2026-12-01 through 2026-12-02 was rejected.");
        verify(auditLogs).save(argThat(audit -> "HR_LEAVE_REVIEWED".equals(audit.getAction())));
    }

    private static CreateStaffRequest request() {
        CreateStaffRequest request = new CreateStaffRequest();
        request.setUserId(7L);
        request.setEmployeeNumber(" EMP-7 ");
        request.setFullName(" A. Teacher ");
        request.setTitle(" Lecturer ");
        request.setHireDate(LocalDate.of(2020, 1, 1));
        return request;
    }

    private static User user(Role role, boolean active) {
        User user = new User("employee", "encoded", role);
        user.setId(7L);
        user.setIsActive(active);
        return user;
    }
}
