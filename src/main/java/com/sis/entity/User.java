package com.sis.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.util.Locale;

@Entity
@Table(name = "users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_users_username_normalized", columnNames = "username_normalized"),
                @UniqueConstraint(name = "uk_users_email_normalized", columnNames = "email_normalized")
        },
        indexes = {
                @Index(name = "idx_users_username_normalized", columnList = "username_normalized"),
                @Index(name = "idx_users_email_normalized", columnList = "email_normalized")
        })
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(name = "username_normalized", nullable = false, length = 255)
    private String usernameNormalized;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    // Email field for user communication
    @Column(unique = true)
    private String email;

    @Column(name = "email_normalized", length = 255)
    private String emailNormalized;

    // Phone number for contact purposes
    @Column
    private String phone;

    // Soft delete flag - if false, user account is disabled
    @Column(nullable = false)
    private Boolean isActive = true;

    @Column(name = "mfa_enabled", nullable = false)
    private Boolean mfaEnabled = false;

    @Column(name = "mfa_secret")
    private String mfaSecret;

    @Column(name = "credential_version", nullable = false)
    private int credentialVersion;

    @Column(name = "password_change_required", nullable = false)
    private boolean passwordChangeRequired;

    public User() {
    }

    public User(String username, String password, Role role) {
        this.username = username;
        this.password = password;
        this.role = role;
        this.isActive = true;
        normalizeIdentityFields();
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

    @JsonIgnore
    public String getUsernameNormalized() {
        return usernameNormalized;
    }

    public void setUsername(String username) {
        this.username = username;
        normalizeIdentityFields();
    }

    public void setUsernameNormalized(String usernameNormalized) {
        this.usernameNormalized = usernameNormalized;
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

    @JsonIgnore
    public String getEmailNormalized() {
        return emailNormalized;
    }

    /**
     * Set user email address
     * @param email user's email
     */
    public void setEmail(String email) {
        this.email = email;
        normalizeIdentityFields();
    }

    public void setEmailNormalized(String emailNormalized) {
        this.emailNormalized = emailNormalized;
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

    public Boolean getMfaEnabled() { return mfaEnabled; }
    public void setMfaEnabled(Boolean mfaEnabled) { this.mfaEnabled = mfaEnabled; }

    @JsonIgnore
    public String getMfaSecret() { return mfaSecret; }
    public void setMfaSecret(String mfaSecret) { this.mfaSecret = mfaSecret; }

    @JsonIgnore
    public int getCredentialVersion() { return credentialVersion; }
    public void setCredentialVersion(int credentialVersion) { this.credentialVersion = credentialVersion; }
    @JsonIgnore
    public boolean isPasswordChangeRequired() { return passwordChangeRequired; }
    public void setPasswordChangeRequired(boolean passwordChangeRequired) {
        this.passwordChangeRequired = passwordChangeRequired;
    }

    @PrePersist
    @PreUpdate
    void normalizeIdentityFields() {
        if (username == null) {
            usernameNormalized = null;
        } else {
            username = username.trim();
            usernameNormalized = username.toLowerCase(Locale.ROOT);
        }
        if (email == null) {
            emailNormalized = null;
        } else {
            email = email.trim();
            emailNormalized = email.toLowerCase(Locale.ROOT);
        }
    }
}
