package com.sis.controller;

import com.sis.dto.AuthRequest;
import com.sis.dto.AuthResponse;
import com.sis.dto.ChangePasswordRequest;
import com.sis.dto.EmailRequest;
import com.sis.dto.MfaSetupResponse;
import com.sis.dto.MfaSetupRequest;
import com.sis.dto.MfaDisableRequest;
import com.sis.dto.MfaVerifyRequest;
import com.sis.dto.ResetPasswordRequest;
import com.sis.dto.ResetTokenRequest;
import com.sis.entity.Role;
import com.sis.entity.User;
import com.sis.security.JwtAuthenticationFilter;
import com.sis.service.JwtService;
import com.sis.service.MfaSecretCipher;
import com.sis.service.RefreshTokenService;
import com.sis.service.SchoolService;
import com.sis.service.AuthenticationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import com.sis.dto.SessionDto;
import com.sis.entity.RefreshToken;
import com.sis.repository.RefreshTokenRepository;
import jakarta.annotation.PostConstruct;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Optional;
import java.util.Map;

/**
 * Authentication Controller - handles login, registration, password reset, email
 * verification, and session refresh/logout.
 * <p>
 * The filter chain permits "/api/auth/**" for login and recovery flows, so
 * account-specific routes must enforce authentication at the method level. In
 * particular, {@link #register} must never let the caller choose their own role.
 * <p>
 * Auth tokens are HttpOnly cookies, not JSON response fields - see
 * {@link #setAuthCookies} and {@link AuthResponse}'s Javadoc for why.
 */
@RestController
@RequestMapping("/api/auth")
@Validated
public class AuthController {

    private static final String REFRESH_TOKEN_COOKIE = "refresh_token";

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final SchoolService schoolService;
    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${app.security.cookie-secure}")
    private boolean cookieSecure;

    @Value("${app.security.cookie-same-site:Strict}")
    private String cookieSameSite;

    @Value("${app.security.mfa.enabled:false}")
    private boolean mfaEnabled;

    @Value("${app.security.mfa.issuer:MIT Portal}")
    private String mfaIssuer;

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private MfaSecretCipher mfaSecretCipher;

    @PostConstruct
    public void validateCookiePolicy() {
        if (!"Strict".equalsIgnoreCase(cookieSameSite)) {
            throw new IllegalStateException(
                    "COOKIE_SAME_SITE must remain Strict while cookie-authenticated requests use Origin validation");
        }
        cookieSameSite = "Strict";
    }

    public AuthController(AuthenticationManager authenticationManager, JwtService jwtService,
                           RefreshTokenService refreshTokenService, SchoolService schoolService,
                           RefreshTokenRepository refreshTokenRepository) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.schoolService = schoolService;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    /**
     * User login endpoint. Sets an HttpOnly access-token cookie (short-lived) and an
     * HttpOnly refresh-token cookie (long-lived, revocable server-side); the response
     * body carries only role/username for the UI to key off, never the token itself.
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody AuthRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );
            User user = schoolService.getUserByUsername(request.getUsername())
                    .orElseThrow(() -> new IllegalStateException("Authenticated user vanished mid-login"));
            String role = user.getRole().name();
            if (mfaEnabled && Boolean.TRUE.equals(user.getMfaEnabled())) {
                if (request.getOtp() == null || request.getOtp().isBlank()) {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(Map.of("message", "MFA verification required", "mfaRequired", true));
                }
                String storedSecret = user.getMfaSecret();
                String secret = mfaSecretCipher.decrypt(storedSecret);
                if (!verifyTotp(secret, request.getOtp())) {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(Map.of("message", "Invalid MFA code", "mfaRequired", true));
                }
                encryptLegacyMfaSecret(user, storedSecret, secret);
            }
            String accessToken = jwtService.generateToken(
                    request.getUsername(), role, user.getCredentialVersion());
            String refreshToken = refreshTokenService.issue(user);

            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.SET_COOKIE, buildAccessCookie(accessToken).toString());
            headers.add(HttpHeaders.SET_COOKIE, buildRefreshCookie(refreshToken).toString());

            return ResponseEntity.ok().headers(headers).body(new AuthResponse(
                    role, request.getUsername(), user.isPasswordChangeRequired()));
        } catch (AuthenticationException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    /**
     * Exchanges a valid refresh-token cookie for a new access-token cookie, without
     * requiring the user to re-enter credentials. This is what makes a 1-hour access
     * token tolerable UX-wise instead of forcing a re-login every hour.
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(HttpServletRequest request) {
        String rawRefreshToken = extractCookie(request, REFRESH_TOKEN_COOKIE);
        Optional<RefreshTokenService.Rotation> rotationOpt = refreshTokenService.rotate(rawRefreshToken);
        if (rotationOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        RefreshTokenService.Rotation rotation = rotationOpt.get();
        User user = rotation.getUser();
        String accessToken = jwtService.generateToken(
                user.getUsername(), user.getRole().name(), user.getCredentialVersion());

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, buildAccessCookie(accessToken).toString());
        headers.add(HttpHeaders.SET_COOKIE, buildRefreshCookie(rotation.getRefreshToken()).toString());

        return ResponseEntity.ok().headers(headers).body(new AuthResponse(
                user.getRole().name(), user.getUsername(), user.isPasswordChangeRequired()));
    }

    /**
     * Logs out: revokes the refresh token server-side (so it can't be replayed even
     * if it leaked) and clears both cookies by setting them to expire immediately.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        String rawRefreshToken = extractCookie(request, REFRESH_TOKEN_COOKIE);
        refreshTokenService.revoke(rawRefreshToken);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, expireCookie(JwtAuthenticationFilter.ACCESS_TOKEN_COOKIE, "/").toString());
        headers.add(HttpHeaders.SET_COOKIE, expireCookie(REFRESH_TOKEN_COOKIE, "/api/auth").toString());

        return ResponseEntity.ok().headers(headers).build();
    }

    @PostMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return authenticationService.changePassword(username, request.getCurrentPassword(), request.getNewPassword())
                ? ResponseEntity.ok().build()
                : ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    @GetMapping("/authorities")
    @PreAuthorize("isAuthenticated()")
    public List<String> authorities() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                .sorted()
                .toList();
    }

    @GetMapping("/mfa/status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Boolean> mfaStatus() {
        if (!mfaEnabled) return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return schoolService.getUserByUsername(username).map(user -> ResponseEntity.ok(Boolean.TRUE.equals(user.getMfaEnabled())))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/mfa/setup")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MfaSetupResponse> setupMfa(@Valid @RequestBody MfaSetupRequest request) {
        if (!mfaEnabled) return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        if (!verifyCurrentPassword(username, request.getCurrentPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return schoolService.getUserByUsername(username).map(user -> {
            if (Boolean.TRUE.equals(user.getMfaEnabled())) {
                return ResponseEntity.status(HttpStatus.CONFLICT).<MfaSetupResponse>build();
            }
            String storedSecret = user.getMfaSecret();
            String secret = storedSecret == null || storedSecret.isBlank()
                    ? null
                    : mfaSecretCipher.decrypt(storedSecret);
            if (!isValidTotpSecret(secret)) {
                secret = generateTotpSecret();
            }
            user.setMfaSecret(mfaSecretCipher.encrypt(secret));
            schoolService.saveUser(user);
            return ResponseEntity.ok(new MfaSetupResponse(secret, buildOtpauthUri(username, secret)));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/mfa/verify")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> verifyMfa(@Valid @RequestBody MfaVerifyRequest request) {
        if (!mfaEnabled) return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        if (!verifyCurrentPassword(username, request.getCurrentPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return schoolService.getUserByUsername(username).map(user -> {
            String storedSecret = user.getMfaSecret();
            String secret = mfaSecretCipher.decrypt(storedSecret);
            if (!verifyTotp(secret, request.getOtp())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).<Void>build();
            }
            encryptLegacyMfaSecret(user, storedSecret, secret);
            user.setMfaEnabled(true);
            revokeSessionsAfterMfaChange(user);
            schoolService.saveUser(user);
            return ResponseEntity.ok().<Void>build();
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/mfa/disable")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> disableMfa(@Valid @RequestBody MfaDisableRequest request) {
        if (!mfaEnabled) return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        if (!verifyCurrentPassword(username, request.getCurrentPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return schoolService.getUserByUsername(username).map(user -> {
            if (Boolean.TRUE.equals(user.getMfaEnabled())
                    && !verifyTotp(mfaSecretCipher.decrypt(user.getMfaSecret()), request.getOtp())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).<Void>build();
            }
            user.setMfaEnabled(false);
            user.setMfaSecret(null);
            revokeSessionsAfterMfaChange(user);
            schoolService.saveUser(user);
            return ResponseEntity.ok().<Void>build();
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/sessions/revoke-all")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> revokeAllSessions() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return schoolService.getUserByUsername(username).map(user -> {
            refreshTokenService.revokeAllForUser(user.getId());
            user.setCredentialVersion(user.getCredentialVersion() + 1);
            schoolService.saveUser(user);
            return ResponseEntity.ok().<Void>build();
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/sessions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<SessionDto>> sessions() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return schoolService.getUserByUsername(username)
                .map(user -> ResponseEntity.ok(refreshTokenRepository.findByUserIdOrderByExpiryDateDesc(user.getId()).stream()
                        .filter(RefreshToken::isValid)
                        .map(token -> new SessionDto(token.getId(), token.getExpiryDate(), true))
                        .toList()))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/sessions/{sessionId}/revoke")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> revokeSession(@PathVariable Long sessionId) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return schoolService.getUserByUsername(username)
                .flatMap(user -> refreshTokenRepository.findById(sessionId)
                        .filter(token -> token.getUser().getId().equals(user.getId())))
                .map(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private ResponseCookie buildAccessCookie(String token) {
        return ResponseCookie.from(JwtAuthenticationFilter.ACCESS_TOKEN_COOKIE, token)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path("/")
                .maxAge(java.time.Duration.ofHours(1))
                .build();
    }

    private ResponseCookie buildRefreshCookie(String token) {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE, token)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                // Scoped to the refresh endpoint only - this cookie never needs to be
                // sent on ordinary API calls, only when actually refreshing.
                .path("/api/auth")
                .maxAge(java.time.Duration.ofDays(7))
                .build();
    }

    private ResponseCookie expireCookie(String name, String path) {
        return ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path(path)
                .maxAge(0)
                .build();
    }

    private String extractCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        for (var cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }

    private boolean verifyTotp(String secret, String otp) {
        if (secret == null || secret.isBlank() || otp == null || otp.isBlank()) {
            return false;
        }
        String candidate = otp.replace(" ", "");
        if (!candidate.matches("[0-9]{6}")) {
            return false;
        }
        long timeStep = Instant.now().getEpochSecond() / 30L;
        for (long offset = -1; offset <= 1; offset++) {
            String expected;
            try {
                expected = generateTotp(secret, timeStep + offset);
            } catch (IllegalArgumentException ex) {
                return false;
            }
            if (java.security.MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.US_ASCII),
                    candidate.getBytes(StandardCharsets.US_ASCII))) {
                return true;
            }
        }
        return false;
    }

    private boolean isValidTotpSecret(String secret) {
        if (secret == null || !secret.matches("[A-Z2-7]{32}")) {
            return false;
        }
        try {
            return decodeBase32(secret).length == 20;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private String generateTotpSecret() {
        byte[] bytes = new byte[20];
        new SecureRandom().nextBytes(bytes);
        return encodeBase32(bytes);
    }

    private String encodeBase32(byte[] bytes) {
        final String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        StringBuilder encoded = new StringBuilder((bytes.length * 8 + 4) / 5);
        int bitBuffer = 0;
        int bitCount = 0;
        for (byte value : bytes) {
            bitBuffer = (bitBuffer << 8) | (value & 0xFF);
            bitCount += 8;
            while (bitCount >= 5) {
                bitCount -= 5;
                encoded.append(alphabet.charAt((bitBuffer >> bitCount) & 0x1F));
            }
        }
        if (bitCount > 0) {
            encoded.append(alphabet.charAt((bitBuffer << (5 - bitCount)) & 0x1F));
        }
        return encoded.toString();
    }

    private void encryptLegacyMfaSecret(User user, String storedSecret, String plaintextSecret) {
        if (storedSecret != null && !mfaSecretCipher.isEncrypted(storedSecret)) {
            user.setMfaSecret(mfaSecretCipher.encrypt(plaintextSecret));
            schoolService.saveUser(user);
        }
    }

    private boolean verifyCurrentPassword(String username, String password) {
        if (password == null || password.isBlank()) {
            return false;
        }
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password));
            return true;
        } catch (AuthenticationException ex) {
            return false;
        }
    }

    private void revokeSessionsAfterMfaChange(User user) {
        refreshTokenService.revokeAllForUser(user.getId());
        user.setCredentialVersion(user.getCredentialVersion() + 1);
    }

    private String buildOtpauthUri(String username, String secret) {
        String label = java.net.URLEncoder.encode(username, StandardCharsets.UTF_8);
        String issuer = java.net.URLEncoder.encode(mfaIssuer, StandardCharsets.UTF_8);
        return "otpauth://totp/" + issuer + ":" + label + "?secret=" + secret + "&issuer=" + issuer;
    }

    private String generateTotp(String secret, long timeStep) {
        byte[] key = decodeBase32(secret);
        byte[] counter = new byte[8];
        long value = timeStep;
        for (int i = 7; i >= 0; i--) {
            counter[i] = (byte) (value & 0xFFL);
            value >>= 8;
        }

        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA1");
            mac.init(new javax.crypto.spec.SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(counter);
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            int otp = binary % 1_000_000;
            return String.format("%06d", otp);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Unable to generate TOTP code", e);
        }
    }

    private byte[] decodeBase32(String input) {
        String normalized = input.replace(" ", "").replace("-", "").toUpperCase();
        int bitCount = 0;
        int bitBuffer = 0;
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (char ch : normalized.toCharArray()) {
            int value = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".indexOf(ch);
            if (value < 0) {
                throw new IllegalArgumentException("Invalid base32 secret");
            }
            bitBuffer = (bitBuffer << 5) | value;
            bitCount += 5;
            while (bitCount >= 8) {
                bitCount -= 8;
                out.write((bitBuffer >> bitCount) & 0xFF);
            }
        }
        return out.toByteArray();
    }

    /**
     * Public self-registration endpoint. Always creates a STUDENT account.
     * <p>
     * SECURITY: this used to accept a {@code Role} as a request parameter chosen by the
     * caller, with no restriction at all - since /api/auth/** is public, any anonymous
     * client could POST {@code /api/auth/register?role=ADMIN} and hand themselves
     * administrator access. ADMIN and TEACHER accounts must only ever be created by an
     * authenticated admin via {@code POST /api/admin/users}, which is protected by
     * {@code @PreAuthorize("hasAuthority('ADMIN')")}.
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody AuthRequest request) {
        if (schoolService.createUser(request.getUsername(), request.getPassword(), Role.STUDENT).isEmpty()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Username already exists");
        }
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<String> requestPasswordReset(@Valid @RequestBody EmailRequest request) {
        authenticationService.requestPasswordReset(request.getEmail());
        return ResponseEntity.ok("If the email is registered, password reset instructions will be sent.");
    }

    @PostMapping("/validate-reset-token")
    public ResponseEntity<Boolean> validateResetToken(@Valid @RequestBody ResetTokenRequest request) {
        boolean isValid = authenticationService.validatePasswordResetToken(request.getToken());
        return ResponseEntity.ok(isValid);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authenticationService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok("Password reset successful. Please log in with your new password.");
    }

    @PostMapping("/verify-email")
    public ResponseEntity<String> verifyEmail(@Valid @RequestBody ResetTokenRequest request) {
        authenticationService.confirmEmail(request.getToken());
        return ResponseEntity.ok("Email verified successfully");
    }

    @PostMapping("/validate-email-token")
    public ResponseEntity<Boolean> validateEmailToken(@Valid @RequestBody ResetTokenRequest request) {
        boolean isValid = authenticationService.validateEmailVerificationToken(request.getToken());
        return ResponseEntity.ok(isValid);
    }

    @PostMapping("/resend-password-reset")
    public ResponseEntity<String> resendPasswordReset(@Valid @RequestBody EmailRequest request) {
        authenticationService.resendPasswordResetEmail(request.getEmail());
        return ResponseEntity.ok("If the email is registered, password reset instructions will be sent.");
    }

    @PostMapping("/resend-email-verification")
    public ResponseEntity<String> resendEmailVerification(@Valid @RequestBody EmailRequest request) {
        authenticationService.resendEmailVerification(request.getEmail());
        return ResponseEntity.ok("If the account exists, a verification email has been sent.");
    }
}
