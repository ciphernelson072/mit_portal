package com.sis.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Date;

/**
 * Issues and validates short-lived JWT access tokens. Long-lived refresh tokens are
 * a separate, opaque, server-side-revocable concern - see {@link RefreshTokenService}.
 */
@Service
public class JwtService {

    private static final int MIN_KEY_BYTES = 32; // HS256's minimum key length (256 bits)

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration-ms}")
    private long expirationMs;

    private Key signingKey;

    /**
     * Fail fast, with a clear message, if the configured secret is too short for
     * HS256 - rather than let {@code Keys.hmacShaKeyFor} throw its much less
     * actionable {@code WeakKeyException} the first time a token is issued (i.e. on
     * the first login attempt, in production, instead of at boot).
     */
    @PostConstruct
    public void init() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MIN_KEY_BYTES) {
            throw new IllegalStateException(
                    "jwt.secret is " + keyBytes.length + " bytes, but HS256 requires at least "
                            + MIN_KEY_BYTES + " bytes (256 bits). Set a longer JWT_SECRET "
                            + "environment variable - e.g. `openssl rand -base64 48`.");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(String username, String role, int credentialVersion) {
        return generateToken(username, role, credentialVersion, false, false);
    }

    public String generateToken(String username, String role, int credentialVersion,
                                boolean mfaRequired, boolean mfaVerified) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .setSubject(username)
                .claim("role", role)
                .claim("credentialVersion", credentialVersion)
                .claim("mfaRequired", mfaRequired)
                .claim("mfaVerified", mfaVerified)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateMfaEnrollmentToken(String username, String role, int credentialVersion) {
        Date now = new Date();
        return Jwts.builder()
                .setSubject(username)
                .claim("role", role)
                .claim("credentialVersion", credentialVersion)
                .claim("mfaRequired", true)
                .claim("mfaVerified", false)
                .claim("mfaEnrollment", true)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + 10 * 60 * 1000L))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(signingKey).build().parseClaimsJws(token);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public String getUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public String getRole(String token) {
        return parseClaims(token).get("role", String.class);
    }

    public Integer getCredentialVersion(String token) {
        Object value = parseClaims(token).get("credentialVersion");
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    public boolean isMfaRequired(String token) {
        return Boolean.TRUE.equals(parseClaims(token).get("mfaRequired", Boolean.class));
    }

    public boolean isMfaVerified(String token) {
        return Boolean.TRUE.equals(parseClaims(token).get("mfaVerified", Boolean.class));
    }

    public boolean isMfaEnrollmentToken(String token) {
        return Boolean.TRUE.equals(parseClaims(token).get("mfaEnrollment", Boolean.class));
    }

    private Claims parseClaims(String token) {
        return Jwts.parserBuilder().setSigningKey(signingKey).build().parseClaimsJws(token).getBody();
    }
}
