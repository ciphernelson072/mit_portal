package com.sis.service;

import com.sis.dto.CreateStaffRequest;
import com.sis.entity.Role;
import com.sis.entity.Staff;
import com.sis.entity.User;
import com.sis.repository.AuditLogRepository;
import com.sis.repository.LeaveRequestRepository;
import com.sis.repository.StaffRepository;
import com.sis.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class HrServiceStaffProvisioningTest {
    private StaffRepository staff;
    private UserRepository users;
    private HrService service;

    @BeforeEach
    void setUp() {
        staff = mock(StaffRepository.class);
        users = mock(UserRepository.class);
        service = new HrService(staff, mock(LeaveRequestRepository.class), users, mock(AuditLogRepository.class));
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
