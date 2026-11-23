package com.sis.dto;

public record EmailVerificationStatusDto(String email, boolean verified, String pendingEmail) {
}
