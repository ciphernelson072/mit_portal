package com.sis.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "mfa_recovery_codes",
        indexes = @Index(name = "idx_mfa_recovery_user", columnList = "user_id"))
public class MfaRecoveryCode {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "code_hash", nullable = false, unique = true, length = 64)
    private String codeHash;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    public MfaRecoveryCode() {}

    public MfaRecoveryCode(User user, String codeHash) {
        this.user = user;
        this.codeHash = codeHash;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getCodeHash() { return codeHash; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUsedAt() { return usedAt; }
    public void setUsedAt(LocalDateTime usedAt) { this.usedAt = usedAt; }
}
