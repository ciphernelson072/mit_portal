package com.sis.dto;

import com.sis.entity.EmailDeliveryStatus;

import java.time.LocalDateTime;

public record EmailDeliveryAdminDto(
        Long id,
        Long userId,
        String notificationTitle,
        EmailDeliveryStatus status,
        int attemptCount,
        LocalDateTime nextAttemptAt,
        String lastError,
        LocalDateTime createdAt,
        LocalDateTime sentAt) {
}
