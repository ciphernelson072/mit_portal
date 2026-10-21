package com.sis.dto;

import com.sis.entity.Role;
import javax.validation.constraints.NotNull;

public class AssignPermissionRequest {
    @NotNull private Role role;
    @NotNull private Long permissionId;
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public Long getPermissionId() { return permissionId; }
    public void setPermissionId(Long permissionId) { this.permissionId = permissionId; }
}