package com.sis.repository;

import com.sis.entity.Role;
import com.sis.entity.RolePermission;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {
    @EntityGraph(attributePaths = "permission")
    List<RolePermission> findByRole(Role role);
    boolean existsByRoleAndPermissionId(Role role, Long permissionId);
    long deleteByRoleAndPermissionId(Role role, Long permissionId);
}