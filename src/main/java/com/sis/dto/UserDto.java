package com.sis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Read-only representation of a User account for admin screens.
 * Deliberately excludes the password hash - the {@code User} entity is never
 * returned from a controller directly.
 */
@Data
@NoArgsConstructor
public class UserDto {
    private Long id;
    private String username;
    private String email;
    private String phone;
    private String role;

    // Explicit @JsonProperty: Lombok's @Data generates isActive() for a boolean field
    // named "active", but Jackson's own bean-naming convention would otherwise decide
    // the JSON key independently of that getter name. Pinning it to "isActive" keeps
    // the API contract stable and matches what the admin frontend already expects.
    @JsonProperty("isActive")
    private boolean active;

    public UserDto(Long id, String username, String email, String phone, String role, boolean active) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.phone = phone;
        this.role = role;
        this.active = active;
    }
}
