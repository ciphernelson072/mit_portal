package com.sis.service;

import com.sis.entity.Permission;
import com.sis.entity.Role;
import com.sis.entity.RolePermission;
import com.sis.entity.User;
import com.sis.repository.RolePermissionRepository;
import com.sis.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationRecipientServiceTest {

    @Test
    void includesBuiltInAdministratorsAndActiveUsersWithRolePermission() {
        UserRepository users = mock(UserRepository.class);
        RolePermissionRepository assignments = mock(RolePermissionRepository.class);
        User admin = user(1L, "admin", Role.ADMIN);
        User hrTeacher = user(2L, "hr-teacher", Role.TEACHER);
        when(assignments.findByRole(Role.TEACHER)).thenReturn(
                List.of(assignment(Role.TEACHER, "HR_MANAGE", true)));
        when(users.findByRoleAndIsActiveTrueOrderByIdAsc(Role.ADMIN)).thenReturn(List.of(admin));
        when(users.findByRoleAndIsActiveTrueOrderByIdAsc(Role.TEACHER)).thenReturn(List.of(hrTeacher));

        NotificationRecipientService service = new NotificationRecipientService(users, assignments);

        assertThat(service.findActiveUsersWithPermission("HR_MANAGE", Role.ADMIN))
                .containsExactly(admin, hrTeacher);
        verify(users).findByRoleAndIsActiveTrueOrderByIdAsc(Role.ADMIN);
        verify(users).findByRoleAndIsActiveTrueOrderByIdAsc(Role.TEACHER);
    }

    @Test
    void inactiveOrUnrelatedRolePermissionsDoNotAddRecipients() {
        UserRepository users = mock(UserRepository.class);
        RolePermissionRepository assignments = mock(RolePermissionRepository.class);
        User admin = user(1L, "admin", Role.ADMIN);
        when(assignments.findByRole(Role.TEACHER)).thenReturn(
                List.of(assignment(Role.TEACHER, "HR_MANAGE", false)));
        when(assignments.findByRole(Role.STUDENT)).thenReturn(
                List.of(assignment(Role.STUDENT, "SERVICE_REQUEST_MANAGE", true)));
        when(users.findByRoleAndIsActiveTrueOrderByIdAsc(Role.ADMIN)).thenReturn(List.of(admin));

        NotificationRecipientService service = new NotificationRecipientService(users, assignments);

        assertThat(service.findActiveUsersWithPermission("HR_MANAGE", Role.ADMIN))
                .containsExactly(admin);
    }

    private static RolePermission assignment(Role role, String code, boolean active) {
        Permission permission = new Permission();
        permission.setCode(code);
        permission.setActive(active);
        RolePermission assignment = new RolePermission();
        assignment.setRole(role);
        assignment.setPermission(permission);
        return assignment;
    }

    private static User user(Long id, String username, Role role) {
        User user = new User(username, "encoded", role);
        user.setId(id);
        return user;
    }
}
