package com.sis.service;

public record UserEmailVerificationRequestedEvent(Long userId, String email) {
}
