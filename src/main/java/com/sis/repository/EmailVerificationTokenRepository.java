package com.sis.repository;

import com.sis.entity.EmailVerificationToken;
import com.sis.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * EmailVerificationToken Repository - Data access for email verification tokens
 */
@Repository
public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

    /**
     * Find token by token string
     * @param token the verification token
     * @return optional token entity
     */
    Optional<EmailVerificationToken> findByToken(String token);

    /**
     * Find token by user
     * @param user the user
     * @return optional token entity
     */
    Optional<EmailVerificationToken> findByUser(User user);

    /**
     * Find unverified token for user
     * @param user the user
     * @return optional token entity
     */
    Optional<EmailVerificationToken> findByUserAndVerifiedFalse(User user);

    /**
     * Find token by email address
     * @param email the email address
     * @return optional token entity
     */
    Optional<EmailVerificationToken> findByEmail(String email);

    /**
     * Delete all tokens for user (cleanup)
     * @param user the user
     */
    void deleteByUser(User user);

    /**
     * Delete all expired tokens (maintenance)
     * @param currentTime current timestamp
     */
    void deleteByExpiryDateLessThan(java.time.LocalDateTime currentTime);
}
