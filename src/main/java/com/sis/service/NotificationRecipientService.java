package com.sis.service;

import com.sis.entity.Permission;
import com.sis.entity.Role;
import com.sis.entity.User;
import com.sis.repository.RolePermissionRepository;
import com.sis.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
public class NotificationRecipientService {

    private final UserRepository users;
    private final RolePermissionRepository rolePermissions;

    public NotificationRecipientService(UserRepository users, RolePermissionRepository rolePermissions) {
        this.users = users;
        this.rolePermissions = rolePermissions;
    }

    public List<User> findActiveUsersWithPermission(String permissionCode, Role... builtInRoles) {
        Set<Role> permittedRoles = EnumSet.noneOf(Role.class);
        if (builtInRoles != null) {
            permittedRoles.addAll(List.of(builtInRoles));
        }
        for (Role role : Role.values()) {
            boolean permissionAssigned = rolePermissions.findByRole(role).stream()
                    .map(assignment -> assignment.getPermission())
                    .filter(permission -> Boolean.TRUE.equals(permission.getActive()))
                    .anyMatch(permission -> permissionCode.equals(permission.getCode()));
            if (permissionAssigned) {
                permittedRoles.add(role);
            }
        }

        List<User> recipients = new ArrayList<>();
        for (Role role : Role.values()) {
            if (permittedRoles.contains(role)) {
                recipients.addAll(users.findByRoleAndIsActiveTrueOrderByIdAsc(role));
            }
        }
        return recipients;
    }
}
