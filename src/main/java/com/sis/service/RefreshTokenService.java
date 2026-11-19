package com.sis.service;

import com.sis.entity.RefreshToken;
import com.sis.entity.User;
import com.sis.repository.RefreshTokenRepository;
import com.sis.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.DisabledException;
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
    private final UserRepository userRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, UserRepository userRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
    }

    /**
     * Starts the user's only active session. The version update serializes this
     * with concurrent logins and refresh rotation across all application instances.
     */
    public IssuedSession startSingleSession(Long userId, boolean mfaVerified) {
        if (userRepository.incrementCredentialVersion(userId) != 1) {
            throw new IllegalArgumentException("User not found");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new DisabledException("User account is disabled");
        }

        refreshTokenRepository.revokeAllForUser(userId);
        return new IssuedSession(createToken(user, mfaVerified), user.getCredentialVersion());
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

        String tokenHash = hash(rawToken);
        Optional<RefreshToken> storedToken = refreshTokenRepository.findByTokenHash(tokenHash);
        if (storedToken.isEmpty() || !storedToken.get().isValid()) return Optional.empty();

        User user = userRepository.findByIdForUpdate(storedToken.get().getUser().getId()).orElse(null);
        if (user == null || !Boolean.TRUE.equals(user.getIsActive())) return Optional.empty();

        RefreshToken current = refreshTokenRepository.findByTokenHash(tokenHash).orElse(null);
        if (current == null || !current.isValid()) return Optional.empty();
        if (refreshTokenRepository.consumeIfValid(current.getId(), LocalDateTime.now()) != 1) {
            return Optional.empty();
        }

        String replacement = createToken(user, current.isMfaVerified());
        return Optional.of(new Rotation(user, replacement, current.isMfaVerified()));
    }

    /** Revokes the current session and invalidates its access tokens immediately. */
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return;
        RefreshToken existing = refreshTokenRepository.findByTokenHash(hash(rawToken)).orElse(null);
        if (existing == null) return;

        User user = userRepository.findByIdForUpdate(existing.getUser().getId()).orElse(null);
        if (user == null) return;
        if (refreshTokenRepository.revokeIfValid(existing.getId(), LocalDateTime.now()) == 1) {
            user.setCredentialVersion(user.getCredentialVersion() + 1);
            userRepository.saveAndFlush(user);
        }
    }

    /** Revokes every refresh token for a user (used on password change / account deactivation). */
    public void revokeAllForUser(Long userId) {
        refreshTokenRepository.revokeAllForUser(userId);
    }

    /** Revokes all sessions and invalidates access tokens immediately. */
    public int revokeAllSessions(Long userId) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        refreshTokenRepository.revokeAllForUser(userId);
        user.setCredentialVersion(user.getCredentialVersion() + 1);
        userRepository.saveAndFlush(user);
        return user.getCredentialVersion();
    }

    /** Revokes a user's selected session, if it is still active. */
    public void revokeSession(Long userId, Long sessionId) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        RefreshToken token = refreshTokenRepository.findById(sessionId).orElse(null);
        if (token == null || !userId.equals(token.getUser().getId())) return;
        if (refreshTokenRepository.revokeIfValid(sessionId, LocalDateTime.now()) == 1) {
            user.setCredentialVersion(user.getCredentialVersion() + 1);
            userRepository.saveAndFlush(user);
        }
    }

    private String createToken(User user, boolean mfaVerified) {
        String rawToken = generateRawToken();
        RefreshToken entity = new RefreshToken(
                hash(rawToken), user, LocalDateTime.now().plusNanos(refreshExpirationMs * 1_000_000L), mfaVerified);
        refreshTokenRepository.save(entity);
        return rawToken;
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

    public static final class IssuedSession {
        private final String refreshToken;
        private final int credentialVersion;

        private IssuedSession(String refreshToken, int credentialVersion) {
            this.refreshToken = refreshToken;
            this.credentialVersion = credentialVersion;
        }

        public String getRefreshToken() {
            return refreshToken;
        }

        public int getCredentialVersion() {
            return credentialVersion;
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
