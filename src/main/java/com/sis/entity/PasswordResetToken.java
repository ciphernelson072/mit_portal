package com.sis.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * PasswordResetToken Entity - Manages password reset functionality
 * Stores temporary tokens for password reset requests with expiration
 */
@Entity
@Table(name = "password_reset_tokens", indexes = {
        @Index(name = "idx_user_id", columnList = "user_id"),
        @Index(name = "idx_token", columnList = "token", unique = true)
})
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique reset token - generated randomly for security
     */
    @Column(name = "token", unique = true, nullable = false, length = 255)
    private String token;

    /**
     * Foreign key to User - the user requesting password reset
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Token expiration timestamp
     * Token is invalid after this time
     */
    @Column(name = "expiry_date", nullable = false)
    private LocalDateTime expiryDate;

    /**
     * Whether token has been used
     * Prevents token reuse for security
     */
    @Column(name = "used", nullable = false)
    private Boolean used = false;

    /**
     * Timestamp when token was created
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Constructors
    public PasswordResetToken() {}

    /**
     * Create a new password reset token
     * @param token the reset token
     * @param user the user
     * @param expiryDate when token expires
     */
    public PasswordResetToken(String token, User user, LocalDateTime expiryDate) {
        this.token = token;
        this.user = user;
        this.expiryDate = expiryDate;
        this.used = false;
        this.createdAt = LocalDateTime.now();
    }

    // Pre-persist lifecycle method
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    /**
     * Check if token is expired
     * @return true if current time is after expiry date
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiryDate);
    }

    /**
     * Check if token is valid (not expired and not used)
     * @return true if token is valid
     */
    public boolean isValid() {
        return !isExpired() && !used;
    }

    // Getters and Setters with Javadoc

    /**
     * Get token ID
     * @return token ID
     */
    public Long getId() {
        return id;
    }

    /**
     * Set token ID
     * @param id token ID
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Get reset token string
     * @return token
     */
    public String getToken() {
        return token;
    }

    /**
     * Set reset token string
     * @param token token
     */
    public void setToken(String token) {
        this.token = token;
    }

    /**
     * Get user
     * @return user object
     */
    public User getUser() {
        return user;
    }

    /**
     * Set user
     * @param user user object
     */
    public void setUser(User user) {
        this.user = user;
    }

    /**
     * Get token expiry date
     * @return expiry date
     */
    public LocalDateTime getExpiryDate() {
        return expiryDate;
    }

    /**
     * Set token expiry date
     * @param expiryDate expiry date
     */
    public void setExpiryDate(LocalDateTime expiryDate) {
        this.expiryDate = expiryDate;
    }

    /**
     * Check if token has been used
     * @return true if used
     */
    public Boolean getUsed() {
        return used;
    }

    /**
     * Set whether token has been used
     * @param used true if used
     */
    public void setUsed(Boolean used) {
        this.used = used;
    }

    /**
     * Get creation timestamp
     * @return creation timestamp
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * Set creation timestamp
     * @param createdAt creation timestamp
     */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * String representation
     * @return formatted string
     */
    @Override
    public String toString() {
        return "PasswordResetToken{" +
                "id=" + id +
                ", token='" + token.substring(0, Math.min(10, token.length())) + "...\'" +
                ", user=" + (user != null ? user.getUsername() : "null") +
                ", expiryDate=" + expiryDate +
                ", used=" + used +
                '}';
    }
}
