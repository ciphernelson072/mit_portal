package com.sis.security;

import com.sis.entity.Role;
import com.sis.entity.RolePermission;
import com.sis.entity.User;
import com.sis.repository.RolePermissionRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Collection;

@Component
public class MfaPolicy {

    private final RolePermissionRepository rolePermissionRepository;

    public MfaPolicy(RolePermissionRepository rolePermissionRepository) {
        this.rolePermissionRepository = rolePermissionRepository;
    }

    public boolean isPrivileged(User user) {
        if (user.getRole() == Role.ADMIN) {
            return true;
        }
        return rolePermissionRepository.findByRole(user.getRole()).stream()
                .map(RolePermission::getPermission)
                .filter(permission -> Boolean.TRUE.equals(permission.getActive()))
                .anyMatch(permission -> isFinancePermission(permission.getCode()));
    }

    public static boolean hasPrivilegedAuthority(Collection<? extends GrantedAuthority> authorities) {
        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> "ADMIN".equals(authority)
                        || authority.startsWith("PERM_FINANCE_"));
    }

    private static boolean isFinancePermission(String permissionCode) {
        return permissionCode != null && permissionCode.startsWith("FINANCE_");
    }
}
