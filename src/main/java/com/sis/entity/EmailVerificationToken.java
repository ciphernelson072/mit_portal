package com.sis.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * EmailVerificationToken Entity - Manages email verification functionality
 * Stores temporary tokens for email address verification
 */
@Entity
@Table(name = "email_verification_tokens", indexes = {
        @Index(name = "idx_user_id_email", columnList = "user_id"),
        @Index(name = "idx_token_email", columnList = "token", unique = true)
})
public class EmailVerificationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique verification token - generated randomly
     */
    @Column(name = "token", unique = true, nullable = false, length = 255)
    private String token;

    /**
     * Foreign key to User - the user requesting email verification
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Email address to be verified
     */
    @Column(name = "email", nullable = false, length = 255)
    private String email;

    /**
     * Token expiration timestamp
     * Token is invalid after this time (typically 24 hours)
     */
    @Column(name = "expiry_date", nullable = false)
    private LocalDateTime expiryDate;

    /**
     * Whether email has been verified
     * Once true, email is confirmed and token is no longer needed
     */
    @Column(name = "verified", nullable = false)
    private Boolean verified = false;

    /**
     * Timestamp when token was created
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Constructors
    public EmailVerificationToken() {}

    /**
     * Create a new email verification token
     * @param token the verification token
     * @param user the user
     * @param email the email to verify
     * @param expiryDate when token expires
     */
    public EmailVerificationToken(String token, User user, String email, LocalDateTime expiryDate) {
        this.token = token;
        this.user = user;
        this.email = email;
        this.expiryDate = expiryDate;
        this.verified = false;
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
     * Check if token is valid (not expired and not verified)
     * @return true if token is valid
     */
    public boolean isValid() {
        return !isExpired() && !verified;
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
     * Get verification token string
     * @return token
     */
    public String getToken() {
        return token;
    }

    /**
     * Set verification token string
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
     * Get email to verify
     * @return email address
     */
    public String getEmail() {
        return email;
    }

    /**
     * Set email to verify
     * @param email email address
     */
    public void setEmail(String email) {
        this.email = email;
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
     * Check if email is verified
     * @return true if verified
     */
    public Boolean getVerified() {
        return verified;
    }

    /**
     * Set whether email is verified
     * @param verified true if verified
     */
    public void setVerified(Boolean verified) {
        this.verified = verified;
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
        return "EmailVerificationToken{" +
                "id=" + id +
                ", email='" + email + '\'' +
                ", user=" + (user != null ? user.getUsername() : "null") +
                ", verified=" + verified +
                ", expiryDate=" + expiryDate +
                '}';
    }
}
