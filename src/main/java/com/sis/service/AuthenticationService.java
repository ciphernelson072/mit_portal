package com.sis.service;

import com.sis.entity.PasswordResetToken;
import com.sis.entity.EmailVerificationToken;
import com.sis.entity.User;
import com.sis.exception.ResourceNotFoundException;
import com.sis.repository.PasswordResetTokenRepository;
import com.sis.repository.EmailVerificationTokenRepository;
import com.sis.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
    private EmailService emailService;

    private static final long PASSWORD_RESET_EXPIRATION_MS = 3600000; // 1 hour
    private static final long EMAIL_VERIFICATION_EXPIRATION_MS = 86400000; // 24 hours

    /**
     * Request password reset
     * Creates reset token and sends email to user
     * @param email user email address
     * @throws ResourceNotFoundException if user not found
     */
    public void requestPasswordReset(String email) {
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            throw new ResourceNotFoundException("User with email " + email + " not found");
        }

        User user = userOpt.get();

        // Delete existing tokens for cleanup
        passwordResetTokenRepository.deleteByUser(user);

        // Generate new reset token
        String token = UUID.randomUUID().toString();
        LocalDateTime expiryDate = LocalDateTime.now().plusSeconds(PASSWORD_RESET_EXPIRATION_MS / 1000);

        PasswordResetToken resetToken = new PasswordResetToken(token, user, expiryDate);
        passwordResetTokenRepository.save(resetToken);

        // Send email
        emailService.sendPasswordReset(email, token, user.getUsername());
        logger.info("Password reset token created for user: " + user.getUsername());
    }

    /**
     * Validate password reset token
     * @param token the reset token to validate
     * @return true if token is valid
     */
    public boolean validatePasswordResetToken(String token) {
        Optional<PasswordResetToken> tokenOpt = passwordResetTokenRepository.findByToken(token);
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
        Optional<PasswordResetToken> tokenOpt = passwordResetTokenRepository.findByToken(token);
        if (tokenOpt.isEmpty() || !tokenOpt.get().isValid()) {
            throw new ResourceNotFoundException("Invalid or expired password reset token");
        }

        PasswordResetToken resetToken = tokenOpt.get();
        User user = resetToken.getUser();

        // Update password
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Mark token as used
        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);

        logger.info("Password reset successful for user: " + user.getUsername());
    }

    /**
     * Request email verification
     * Creates verification token and sends email
     * @param user the user
     * @param email email to verify
     */
    public void requestEmailVerification(User user, String email) {
        // Delete existing tokens for this email
        Optional<EmailVerificationToken> existingOpt = emailVerificationTokenRepository.findByEmail(email);
        if (existingOpt.isPresent()) {
            emailVerificationTokenRepository.delete(existingOpt.get());
        }

        // Generate verification token
        String token = UUID.randomUUID().toString();
        LocalDateTime expiryDate = LocalDateTime.now().plusSeconds(EMAIL_VERIFICATION_EXPIRATION_MS / 1000);

        EmailVerificationToken verificationToken = new EmailVerificationToken(token, user, email, expiryDate);
        emailVerificationTokenRepository.save(verificationToken);

        // Send email
        emailService.sendEmailVerification(email, token, user.getUsername());
        logger.info("Email verification token created for user: " + user.getUsername());
    }

    /**
     * Validate email verification token
     * @param token the verification token
     * @return true if token is valid
     */
    public boolean validateEmailVerificationToken(String token) {
        Optional<EmailVerificationToken> tokenOpt = emailVerificationTokenRepository.findByToken(token);
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
        Optional<EmailVerificationToken> tokenOpt = emailVerificationTokenRepository.findByToken(token);
        if (tokenOpt.isEmpty() || !tokenOpt.get().isValid()) {
            throw new ResourceNotFoundException("Invalid or expired email verification token");
        }

        EmailVerificationToken verificationToken = tokenOpt.get();
        User user = verificationToken.getUser();

        // Update user email
        user.setEmail(verificationToken.getEmail());
        userRepository.save(user);

        // Mark token as verified
        verificationToken.setVerified(true);
        emailVerificationTokenRepository.save(verificationToken);

        logger.info("Email verified for user: " + user.getUsername());
    }

    /**
     * Get user from password reset token
     * @param token the reset token
     * @return user object
     * @throws ResourceNotFoundException if token invalid
     */
    public User getUserFromPasswordResetToken(String token) {
        Optional<PasswordResetToken> tokenOpt = passwordResetTokenRepository.findByToken(token);
        if (tokenOpt.isEmpty() || !tokenOpt.get().isValid()) {
            throw new ResourceNotFoundException("Invalid or expired password reset token");
        }
        return tokenOpt.get().getUser();
    }

    /**
     * Get user from email verification token
     * @param token the verification token
     * @return user object
     * @throws ResourceNotFoundException if token invalid
     */
    public User getUserFromEmailVerificationToken(String token) {
        Optional<EmailVerificationToken> tokenOpt = emailVerificationTokenRepository.findByToken(token);
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
        Optional<EmailVerificationToken> tokenOpt = emailVerificationTokenRepository.findByUser(user);
        if (tokenOpt.isEmpty()) {
            return false;
        }
        return tokenOpt.get().getVerified();
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
     * Resend email verification
     * @param email the email to verify
     * @throws ResourceNotFoundException if user not found
     */
    public void resendEmailVerification(String email) {
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            userOpt = userRepository.findAll().stream()
                    .filter(u -> u.getEmail() != null && u.getEmail().equals(email))
                    .findFirst();
        }

        if (userOpt.isEmpty()) {
            throw new ResourceNotFoundException("User with email " + email + " not found");
        }

        requestEmailVerification(userOpt.get(), email);
    }
}
