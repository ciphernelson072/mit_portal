package com.sis.service;

import com.sis.dto.AssignPermissionRequest;
import com.sis.dto.CreatePermissionRequest;
import com.sis.entity.AuditLog;
import com.sis.entity.Permission;
import com.sis.entity.RolePermission;
import com.sis.repository.AuditLogRepository;
import com.sis.repository.PermissionRepository;
import com.sis.repository.RolePermissionRepository;
import com.sis.repository.UserRepository;
import com.sis.security.PermissionAuthorityPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class PermissionService {
    private final PermissionRepository permissions;
    private final RolePermissionRepository rolePermissions;
    private final AuditLogRepository auditLogs;
    private final UserRepository users;

    public PermissionService(PermissionRepository permissions, RolePermissionRepository rolePermissions,
                             AuditLogRepository auditLogs, UserRepository users) {
        this.permissions = permissions;
        this.rolePermissions = rolePermissions;
        this.auditLogs = auditLogs;
        this.users = users;
    }

    public Permission create(CreatePermissionRequest request, String createdBy) {
        String code = request.getCode().trim().toUpperCase(java.util.Locale.ROOT);
        if (PermissionAuthorityPolicy.isReservedRoleAuthority(code)) {
            throw new IllegalArgumentException("Permission code cannot shadow a built-in role authority.");
        }
        Permission permission = new Permission();
        permission.setCode(code);
        permission.setDescription(request.getDescription());
        Permission saved = permissions.save(permission);
        audit(createdBy, "PERMISSION_CREATED", "Permission " + saved.getCode() + " created");
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Permission> list() { return permissions.findAll(); }

    public RolePermission assign(AssignPermissionRequest request, String assignedBy) {
        if (rolePermissions.existsByRoleAndPermissionId(request.getRole(), request.getPermissionId())) throw new IllegalStateException("Permission is already assigned to this role");
        RolePermission assignment = new RolePermission();
        assignment.setRole(request.getRole());
        assignment.setPermission(permissions.findById(request.getPermissionId()).orElseThrow(() -> new IllegalArgumentException("Permission not found")));
        RolePermission saved = rolePermissions.save(assignment);
        audit(assignedBy, "ROLE_PERMISSION_ASSIGNED",
                request.getRole().name() + " granted " + saved.getPermission().getCode());
        return saved;
    }

    public void revoke(AssignPermissionRequest request, String revokedBy) {
        Permission permission = permissions.findById(request.getPermissionId())
                .orElseThrow(() -> new IllegalArgumentException("Permission not found"));
        long removed = rolePermissions.deleteByRoleAndPermissionId(request.getRole(), request.getPermissionId());
        if (removed > 0) {
            audit(revokedBy, "ROLE_PERMISSION_REVOKED",
                    request.getRole().name() + " revoked " + permission.getCode());
        }
    }

    private void audit(String username, String action, String details) {
        Long userId = users.findByUsernameNormalized(username)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + username))
                .getId();
        auditLogs.save(new AuditLog(userId, action, details));
    }

    @Transactional(readOnly = true)
    public List<RolePermission> assignments(com.sis.entity.Role role) { return rolePermissions.findByRole(role); }
}