package com.sis.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import javax.persistence.*;

@Entity
@Table(name = "users")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    // Email field for user communication
    @Column(unique = true)
    private String email;

    // Phone number for contact purposes
    @Column
    private String phone;

    // Soft delete flag - if false, user account is disabled
    @Column(nullable = false, columnDefinition = "TINYINT(1) DEFAULT 1")
    private Boolean isActive = true;

    public User() {
    }

    public User(String username, String password, Role role) {
        this.username = username;
        this.password = password;
        this.role = role;
        this.isActive = true;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    /**
     * Get user email address
     * @return email
     */
    public String getEmail() {
        return email;
    }

    /**
     * Set user email address
     * @param email user's email
     */
    public void setEmail(String email) {
        this.email = email;
    }

    /**
     * Get user phone number
     * @return phone number
     */
    public String getPhone() {
        return phone;
    }

    /**
     * Set user phone number
     * @param phone user's phone number
     */
    public void setPhone(String phone) {
        this.phone = phone;
    }

    /**
     * Check if user account is active
     * @return true if active, false if soft-deleted
     */
    public Boolean getIsActive() {
        return isActive;
    }

    /**
     * Set active status for user account (soft delete support)
     * @param isActive true to activate, false to deactivate
     */
    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }
}
