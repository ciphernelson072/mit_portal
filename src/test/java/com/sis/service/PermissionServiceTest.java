package com.sis.service;

import com.sis.dto.CreatePermissionRequest;
import com.sis.repository.AuditLogRepository;
import com.sis.repository.PermissionRepository;
import com.sis.repository.RolePermissionRepository;
import com.sis.repository.UserRepository;
import com.sis.entity.Permission;
import com.sis.entity.Role;
import com.sis.entity.RolePermission;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class PermissionServiceTest {

    private final PermissionRepository permissions = mock(PermissionRepository.class);
    private final RolePermissionRepository assignments = mock(RolePermissionRepository.class);
    private final PermissionService service = new PermissionService(
            permissions, assignments, mock(AuditLogRepository.class), mock(UserRepository.class));

    @Test
    void cannotCreatePermissionThatShadowsBuiltInRoleAuthority() {
        for (String code : new String[]{"ADMIN", " teacher ", "PERM_STUDENT"}) {
            CreatePermissionRequest request = new CreatePermissionRequest();
            request.setCode(code);
            request.setDescription("Role alias");

            assertThatThrownBy(() -> service.create(request, "admin"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("built-in role");
        }
        verify(permissions, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void legacyRoleAliasPermissionsAreNotLoadedAsDelegatedAuthorities() {
        UserRepository users = mock(UserRepository.class);
        RolePermissionRepository assignments = mock(RolePermissionRepository.class);
        CustomUserDetailsService details = new CustomUserDetailsService(users, assignments);
        com.sis.entity.User user = new com.sis.entity.User("teacher", "encoded", Role.TEACHER);
        Permission adminAlias = permission("ADMIN");
        Permission validPermission = permission("ATTENDANCE_MANAGE");
        when(users.findByUsernameIgnoreCase("teacher")).thenReturn(java.util.Optional.of(user));
        when(assignments.findByRole(Role.TEACHER)).thenReturn(List.of(
                assignment(adminAlias), assignment(validPermission)));

        var authorities = details.loadUserByUsername("teacher").getAuthorities();

        assertThat(authorities).extracting("authority")
                .contains("TEACHER", "PERM_ATTENDANCE_MANAGE")
                .doesNotContain("PERM_ADMIN");
    }

    private Permission permission(String code) {
        Permission permission = new Permission();
        permission.setCode(code);
        permission.setActive(true);
        return permission;
    }

    private RolePermission assignment(Permission permission) {
        RolePermission assignment = new RolePermission();
        assignment.setRole(Role.TEACHER);
        assignment.setPermission(permission);
        return assignment;
    }
}
