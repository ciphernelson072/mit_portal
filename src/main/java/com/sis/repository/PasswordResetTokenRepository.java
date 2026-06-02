package com.sis.repository;

import com.sis.entity.PasswordResetToken;
import com.sis.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * PasswordResetToken Repository - Data access for password reset tokens
 */
@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    /**
     * Find token by token string
     * @param token the reset token
     * @return optional token entity
     */
    Optional<PasswordResetToken> findByToken(String token);

    /**
     * Find token by user
     * @param user the user
     * @return optional token entity
     */
    Optional<PasswordResetToken> findByUser(User user);

    /**
     * Find unused valid token for user
     * @param user the user
     * @return optional token entity
     */
    Optional<PasswordResetToken> findByUserAndUsedFalse(User user);

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
