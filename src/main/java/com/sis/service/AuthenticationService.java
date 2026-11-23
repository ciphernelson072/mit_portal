package com.sis.service;

import com.sis.entity.PasswordResetToken;
import com.sis.entity.EmailVerificationToken;
import com.sis.entity.User;
import com.sis.dto.EmailVerificationStatusDto;
import com.sis.exception.ResourceNotFoundException;
import com.sis.exception.EmailDeliveryException;
import com.sis.repository.PasswordResetTokenRepository;
import com.sis.repository.EmailVerificationTokenRepository;
import com.sis.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Authentication Service - Handles password reset, email verification, and auth-related operations
 * Manages security tokens for password reset and email verification
 */
@Service
@Transactional
public class AuthenticationService {

    private static final Logger logger = Logger.getLogger(AuthenticationService.class.getName());

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private EmailService emailService;

    private static final long PASSWORD_RESET_EXPIRATION_MS = 3600000; // 1 hour
    private static final long EMAIL_VERIFICATION_EXPIRATION_MS = 86400000; // 24 hours

    /**
     * Request password reset
     * Creates reset token and sends email to user
     * @param email user email address
     * Unknown addresses intentionally produce the same outcome as known addresses
     * so this endpoint cannot be used to enumerate accounts.
     */
    public void requestPasswordReset(String email) {
        String normalizedEmail = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
        Optional<User> userOpt = normalizedEmail == null ? Optional.empty() : userRepository.findByEmailIgnoreCase(normalizedEmail);
        if (userOpt.isEmpty() || !userOpt.get().isEmailVerified()) {
            return;
        }

        User user = userOpt.get();

        // Delete existing tokens for cleanup
        passwordResetTokenRepository.deleteByUser(user);

        // Generate new reset token
        String token = UUID.randomUUID().toString();
        LocalDateTime expiryDate = LocalDateTime.now().plusSeconds(PASSWORD_RESET_EXPIRATION_MS / 1000);

        PasswordResetToken resetToken = new PasswordResetToken(hashResetToken(token), user, expiryDate);
        passwordResetTokenRepository.save(resetToken);

        // Send email
        emailService.sendPasswordReset(user.getEmail(), token, user.getUsername());
        logger.info(() -> "Password reset token created for user: " + user.getUsername());
    }

    /**
     * Validate password reset token
     * @param token the reset token to validate
     * @return true if token is valid
     */
    public boolean validatePasswordResetToken(String token) {
        Optional<PasswordResetToken> tokenOpt = passwordResetTokenRepository.findByToken(hashResetToken(token));
        if (tokenOpt.isEmpty()) {
            return false;
        }

        PasswordResetToken resetToken = tokenOpt.get();
        return resetToken.isValid();
    }

    /**
     * Reset password using token
     * @param token the reset token
     * @param newPassword new password
     * @throws ResourceNotFoundException if token invalid or expired
     */
    public void resetPassword(String token, String newPassword) {
        Optional<PasswordResetToken> tokenOpt = passwordResetTokenRepository.findByToken(hashResetToken(token));
        if (tokenOpt.isEmpty() || !tokenOpt.get().isValid()) {
            throw new ResourceNotFoundException("Invalid or expired password reset token");
        }

        PasswordResetToken resetToken = tokenOpt.get();
        if (passwordResetTokenRepository.consumeIfValid(resetToken.getId(), LocalDateTime.now()) != 1) {
            throw new ResourceNotFoundException("Invalid or expired password reset token");
        }

        User user = userRepository.findByIdForUpdate(resetToken.getUser().getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found for password reset"));

        // Update password
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordChangeRequired(false);
        user.setCredentialVersion(user.getCredentialVersion() + 1);
        userRepository.save(user);

        // Mark token as used
        // A password reset is frequently a response to a compromised account - any
        // session established before the reset (via a refresh token minted with the
        // old credentials) should not remain valid after it.
        refreshTokenService.revokeAllForUser(user.getId());

        logger.info(() -> "Password reset successful for user: " + user.getUsername());
    }

    public boolean changePassword(String username, String currentPassword, String newPassword) {
        String normalizedUsername = username == null ? null : username.trim();
        Optional<User> userOpt = normalizedUsername == null ? Optional.empty() : userRepository.findByUsernameIgnoreCase(normalizedUsername);
        if (userOpt.isEmpty() || !passwordEncoder.matches(currentPassword, userOpt.get().getPassword())) {
            return false;
        }

        User user = userRepository.findByIdForUpdate(userOpt.get().getId()).orElse(null);
        if (user == null || !passwordEncoder.matches(currentPassword, user.getPassword())) {
            return false;
        }
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new IllegalArgumentException("New password must be different from the current password");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordChangeRequired(false);
        user.setCredentialVersion(user.getCredentialVersion() + 1);
        userRepository.save(user);
        refreshTokenService.revokeAllForUser(user.getId());
        return true;
    }

    /**
     * Request email verification
     * Creates verification token and sends email
     * @param user the user
     * @param email email to verify
     */
    @Transactional(noRollbackFor = EmailDeliveryException.class)
    public void requestEmailVerification(User user, String email) {
        String verificationEmail = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
        if (verificationEmail == null || verificationEmail.isBlank()) {
            throw new IllegalArgumentException("A valid email address is required for verification");
        }
        Optional<User> currentOwner = userRepository.findByEmailIgnoreCase(verificationEmail);
        if (currentOwner.isPresent() && !Objects.equals(currentOwner.get().getId(), user.getId())) {
            throw new IllegalArgumentException("Email is already associated with another user");
        }
        Optional<EmailVerificationToken> pendingOwner =
                emailVerificationTokenRepository.findByEmailIgnoreCase(verificationEmail);
        if (pendingOwner.isPresent()
                && !Objects.equals(pendingOwner.get().getUser().getId(), user.getId())
                && !Boolean.TRUE.equals(pendingOwner.get().getVerified())) {
            throw new IllegalArgumentException("Email is already pending verification for another user");
        }
        emailVerificationTokenRepository.deleteByUser(user);

        String token = UUID.randomUUID().toString();
        LocalDateTime expiryDate = LocalDateTime.now().plusSeconds(EMAIL_VERIFICATION_EXPIRATION_MS / 1000);

        EmailVerificationToken verificationToken = new EmailVerificationToken(
                hashVerificationToken(token), user, verificationEmail, expiryDate);
        emailVerificationTokenRepository.save(verificationToken);

        emailService.sendEmailVerification(verificationEmail, token, user.getUsername());
        logger.info(() -> "Email verification token created for user: " + user.getUsername());
    }

    /**
     * Validate email verification token
     * @param token the verification token
     * @return true if token is valid
     */
    public boolean validateEmailVerificationToken(String token) {
        Optional<EmailVerificationToken> tokenOpt =
                emailVerificationTokenRepository.findByToken(hashVerificationToken(token));
        if (tokenOpt.isEmpty()) {
            return false;
        }

        EmailVerificationToken verificationToken = tokenOpt.get();
        return verificationToken.isValid();
    }

    /**
     * Confirm email using verification token
     * @param token the verification token
     * @throws ResourceNotFoundException if token invalid or expired
     */
    public void confirmEmail(String token) {
        Optional<EmailVerificationToken> tokenOpt =
                emailVerificationTokenRepository.findByToken(hashVerificationToken(token));
        if (tokenOpt.isEmpty() || !tokenOpt.get().isValid()) {
            throw new ResourceNotFoundException("Invalid or expired email verification token");
        }

        EmailVerificationToken verificationToken = tokenOpt.get();
        Optional<User> currentOwner = userRepository.findByEmailIgnoreCase(verificationToken.getEmail());
        if (currentOwner.isPresent()
                && !Objects.equals(currentOwner.get().getId(), verificationToken.getUser().getId())) {
            throw new IllegalArgumentException("Email is already associated with another user");
        }
        if (emailVerificationTokenRepository.consumeIfValid(verificationToken.getId(), LocalDateTime.now()) != 1) {
            throw new ResourceNotFoundException("Invalid or expired email verification token");
        }

        User user = verificationToken.getUser();
        user.setEmail(verificationToken.getEmail());
        user.setEmailVerified(true);
        userRepository.save(user);
        emailVerificationTokenRepository.delete(verificationToken);

        logger.info(() -> "Email verified for user: " + user.getUsername());
    }

    /**
     * Get user from password reset token
     * @param token the reset token
     * @return user object
     * @throws ResourceNotFoundException if token invalid
     */
    public User getUserFromPasswordResetToken(String token) {
        Optional<PasswordResetToken> tokenOpt = passwordResetTokenRepository.findByToken(hashResetToken(token));
        if (tokenOpt.isEmpty() || !tokenOpt.get().isValid()) {
            throw new ResourceNotFoundException("Invalid or expired password reset token");
        }
        return tokenOpt.get().getUser();
    }

    private String hashResetToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private String hashVerificationToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Get user from email verification token
     * @param token the verification token
     * @return user object
     * @throws ResourceNotFoundException if token invalid
     */
    public User getUserFromEmailVerificationToken(String token) {
        Optional<EmailVerificationToken> tokenOpt =
                emailVerificationTokenRepository.findByToken(hashVerificationToken(token));
        if (tokenOpt.isEmpty() || !tokenOpt.get().isValid()) {
            throw new ResourceNotFoundException("Invalid or expired email verification token");
        }
        return tokenOpt.get().getUser();
    }

    /**
     * Clean up expired tokens (should be called periodically)
     * @return number of tokens deleted
     */
    public int cleanupExpiredTokens() {
        LocalDateTime now = LocalDateTime.now();

        // Delete expired password reset tokens
        passwordResetTokenRepository.deleteByExpiryDateLessThan(now);

        // Delete expired email verification tokens
        emailVerificationTokenRepository.deleteByExpiryDateLessThan(now);

        logger.info("Expired tokens cleaned up");
        return 0; // Return value for logging purposes
    }

    /**
     * Check if email is verified for user
     * @param user the user
     * @return true if email is verified
     */
    public boolean isEmailVerified(User user) {
        return user.isEmailVerified();
    }

    public EmailVerificationStatusDto getEmailVerificationStatus(User user) {
        Optional<EmailVerificationToken> pending = emailVerificationTokenRepository
                .findFirstByUserAndVerifiedFalseOrderByCreatedAtDesc(user);
        boolean verified = user.isEmailVerified() && pending.isEmpty();
        String pendingEmail = pending.map(EmailVerificationToken::getEmail)
                .orElse(verified ? null : user.getEmail());
        return new EmailVerificationStatusDto(user.getEmail(), verified, pendingEmail);
    }

    /**
     * Resend password reset email
     * @param email user email
     * @throws ResourceNotFoundException if user not found
     */
    public void resendPasswordResetEmail(String email) {
        requestPasswordReset(email);
    }

    /**
     * Resend email verification without revealing whether an email belongs to an account.
     * @param email the email to verify
     */
    public void resendEmailVerification(String email) {
        String normalizedEmail = email == null ? null : email.trim();
        if (normalizedEmail == null || normalizedEmail.isEmpty()) {
            return;
        }

        Optional<EmailVerificationToken> pendingToken =
                emailVerificationTokenRepository.findByEmailIgnoreCase(normalizedEmail)
                        .filter(token -> !Boolean.TRUE.equals(token.getVerified()));
        if (pendingToken.isPresent()) {
            EmailVerificationToken token = pendingToken.get();
            requestEmailVerification(token.getUser(), token.getEmail());
            return;
        }

        Optional<User> userOpt = userRepository.findByEmailNormalized(normalizedEmail)
                .filter(user -> !user.isEmailVerified());
        if (userOpt.isPresent()) {
            requestEmailVerification(userOpt.get(), userOpt.get().getEmail());
        }
    }
}
