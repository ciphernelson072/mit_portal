package com.sis.service;

import com.sis.entity.RefreshToken;
import com.sis.entity.User;
import com.sis.repository.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

/**
 * Manages long-lived refresh tokens: opaque random strings (not JWTs - they carry no
 * claims of their own, they're just a lookup key), stored hashed, that back the
 * HttpOnly-cookie auth flow's ability to mint new short-lived access tokens without
 * requiring the user to log in again every hour, while still being genuinely
 * revocable server-side (logout, password change, account deactivation) in a way a
 * bare JWT never is.
 */
@Service
@Transactional
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    /** Issues a new refresh token for the user, storing only its hash. */
    public String issue(User user) {
        return issue(user, false);
    }

    /** Issues a refresh token that retains whether MFA was completed at login. */
    public String issue(User user, boolean mfaVerified) {
        String rawToken = generateRawToken();
        RefreshToken entity = new RefreshToken(
                hash(rawToken), user, LocalDateTime.now().plusNanos(refreshExpirationMs * 1_000_000L), mfaVerified);
        refreshTokenRepository.save(entity);
        return rawToken;
    }

    /** Returns the associated user if the raw token is valid (exists, unexpired, unrevoked). */
    public Optional<User> validate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return Optional.empty();
        return refreshTokenRepository.findByTokenHash(hash(rawToken))
                .filter(RefreshToken::isValid)
                .map(RefreshToken::getUser);
    }

    /**
     * Atomically consumes a refresh token and issues its replacement. The
     * conditional repository update prevents concurrent requests from successfully
     * rotating the same token more than once.
     */
    public Optional<Rotation> rotate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return Optional.empty();

        Optional<RefreshToken> storedToken = refreshTokenRepository.findByTokenHash(hash(rawToken));
        if (storedToken.isEmpty() || !storedToken.get().isValid()) return Optional.empty();

        RefreshToken current = storedToken.get();
        if (refreshTokenRepository.consumeIfValid(current.getId(), LocalDateTime.now()) != 1) {
            return Optional.empty();
        }

        User user = current.getUser();
        if (!Boolean.TRUE.equals(user.getIsActive())) return Optional.empty();

        String replacement = issue(user, current.isMfaVerified());
        return Optional.of(new Rotation(user, replacement, current.isMfaVerified()));
    }

    /** Revokes a single refresh token (used on logout, so this exact session's token can't be reused). */
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return;
        refreshTokenRepository.findByTokenHash(hash(rawToken))
                .ifPresent(t -> { t.setRevoked(true); refreshTokenRepository.save(t); });
    }

    /** Revokes every refresh token for a user (used on password change / account deactivation). */
    public void revokeAllForUser(Long userId) {
        refreshTokenRepository.revokeAllForUser(userId);
    }

    private String generateRawToken() {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public static final class Rotation {
        private final User user;
        private final String refreshToken;
        private final boolean mfaVerified;

        private Rotation(User user, String refreshToken, boolean mfaVerified) {
            this.user = user;
            this.refreshToken = refreshToken;
            this.mfaVerified = mfaVerified;
        }

        public User getUser() {
            return user;
        }

        public String getRefreshToken() {
            return refreshToken;
        }

        public boolean isMfaVerified() {
            return mfaVerified;
        }
    }
}
