package com.sis.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "role_permissions", uniqueConstraints = @UniqueConstraint(name = "uk_role_permission", columnNames = {"role_name", "permission_id"}))
public class RolePermission {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Enumerated(EnumType.STRING) @Column(name = "role_name", nullable = false, length = 50) private Role role;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "permission_id", nullable = false) private Permission permission;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public Permission getPermission() { return permission; }
    public void setPermission(Permission permission) { this.permission = permission; }
}