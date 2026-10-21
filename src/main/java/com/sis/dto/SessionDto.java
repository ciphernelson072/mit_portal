package com.sis.dto;

import java.time.LocalDateTime;

public class SessionDto {
    private final Long id;
    private final LocalDateTime expiresAt;
    private final boolean active;

    public SessionDto(Long id, LocalDateTime expiresAt, boolean active) {
        this.id = id;
        this.expiresAt = expiresAt;
        this.active = active;
    }

    public Long getId() { return id; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public boolean isActive() { return active; }
}
