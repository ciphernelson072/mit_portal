package com.sis.security;

import com.sis.entity.Role;

import java.util.Locale;

public final class PermissionAuthorityPolicy {

    private PermissionAuthorityPolicy() {
    }

    public static boolean isReservedRoleAuthority(String permissionCode) {
        if (permissionCode == null) {
            return false;
        }
        String normalized = permissionCode.trim().toUpperCase(Locale.ROOT);
        if (normalized.startsWith("PERM_")) {
            normalized = normalized.substring("PERM_".length());
        }
        for (Role role : Role.values()) {
            if (role.name().equals(normalized)) {
                return true;
            }
        }
        return false;
    }
}
