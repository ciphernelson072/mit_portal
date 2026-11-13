package com.sis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Read-only representation of a User account for admin screens.
 * Deliberately excludes the password hash - the {@code User} entity is never
 * returned from a controller directly.
 */
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

    public UserDto() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
