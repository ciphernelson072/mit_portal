package com.sis.security;

import com.sis.entity.Permission;
import com.sis.entity.Role;
import com.sis.entity.RolePermission;
import com.sis.entity.User;
import com.sis.repository.RolePermissionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MfaPolicyTest {

    @Test
    void administratorsAndActiveFinancePermissionHoldersArePrivileged() {
        RolePermissionRepository repository = mock(RolePermissionRepository.class);
        MfaPolicy policy = new MfaPolicy(repository);

        User administrator = new User("admin", "encoded", Role.ADMIN);
        assertTrue(policy.isPrivileged(administrator));

        Permission permission = new Permission();
        permission.setCode("FINANCE_PAYMENT_CONFIRM");
        permission.setActive(true);
        RolePermission assignment = new RolePermission();
        assignment.setPermission(permission);
        when(repository.findByRole(Role.TEACHER)).thenReturn(List.of(assignment));

        User financeOfficer = new User("finance-officer", "encoded", Role.TEACHER);
        assertTrue(policy.isPrivileged(financeOfficer));
        assertTrue(MfaPolicy.hasPrivilegedAuthority(
                List.of(new SimpleGrantedAuthority("PERM_FINANCE_PAYMENT_CONFIRM"))));
    }

    @Test
    void inactiveFinancePermissionDoesNotRequirePrivilegedMfa() {
        RolePermissionRepository repository = mock(RolePermissionRepository.class);
        Permission permission = new Permission();
        permission.setCode("FINANCE_PAYMENT_CONFIRM");
        permission.setActive(false);
        RolePermission assignment = new RolePermission();
        assignment.setPermission(permission);
        when(repository.findByRole(Role.TEACHER)).thenReturn(List.of(assignment));

        assertFalse(new MfaPolicy(repository).isPrivileged(
                new User("teacher", "encoded", Role.TEACHER)));
        assertFalse(MfaPolicy.hasPrivilegedAuthority(
                List.of(new SimpleGrantedAuthority("TEACHER"))));
    }
}
