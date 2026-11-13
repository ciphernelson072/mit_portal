package com.sis.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "expiry_date", nullable = false)
    private LocalDateTime expiryDate;

    @Column(nullable = false)
    private boolean revoked = false;

    @Column(name = "mfa_verified", nullable = false)
    private boolean mfaVerified;

    public RefreshToken() {
    }

    public RefreshToken(String tokenHash, User user, LocalDateTime expiryDate) {
        this(tokenHash, user, expiryDate, false);
    }

    public RefreshToken(String tokenHash, User user, LocalDateTime expiryDate, boolean mfaVerified) {
        this.tokenHash = tokenHash;
        this.user = user;
        this.expiryDate = expiryDate;
        this.mfaVerified = mfaVerified;
    }

    public Long getId() {
        return id;
    }

    @JsonIgnore
    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public LocalDateTime getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(LocalDateTime expiryDate) {
        this.expiryDate = expiryDate;
    }

    public boolean isRevoked() {
        return revoked;
    }

    public void setRevoked(boolean revoked) {
        this.revoked = revoked;
    }

    public boolean isMfaVerified() {
        return mfaVerified;
    }

    public void setMfaVerified(boolean mfaVerified) {
        this.mfaVerified = mfaVerified;
    }

    public boolean isValid() {
        return !revoked && expiryDate.isAfter(LocalDateTime.now());
    }
}
