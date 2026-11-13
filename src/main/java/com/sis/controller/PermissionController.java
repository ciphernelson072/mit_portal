package com.sis.controller;

import com.sis.dto.AssignPermissionRequest;
import com.sis.dto.CreatePermissionRequest;
import com.sis.entity.Permission;
import com.sis.entity.Role;
import com.sis.entity.RolePermission;
import com.sis.service.PermissionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/permissions")
@PreAuthorize("@permissionSecurity.canManageRolePermissions()")
public class PermissionController {
    private final PermissionService permissions;

    public PermissionController(PermissionService permissions) { this.permissions = permissions; }

    @PostMapping
    public ResponseEntity<Permission> create(@Valid @RequestBody CreatePermissionRequest request) { return ResponseEntity.status(201).body(permissions.create(request, username())); }

    @GetMapping
    public List<Permission> list() { return permissions.list(); }

    @PostMapping("/assign")
    public ResponseEntity<RolePermission> assign(@Valid @RequestBody AssignPermissionRequest request) { return ResponseEntity.status(201).body(permissions.assign(request, username())); }

    @DeleteMapping("/assign")
    public ResponseEntity<Void> revoke(@Valid @RequestBody AssignPermissionRequest request) { permissions.revoke(request, username()); return ResponseEntity.noContent().build(); }

    @GetMapping("/roles/{role}")
    public List<RolePermission> assignments(@PathVariable Role role) { return permissions.assignments(role); }

    private String username() { return SecurityContextHolder.getContext().getAuthentication().getName(); }
}