package com.sis.repository;

import com.sis.entity.EmailVerificationToken;
import com.sis.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
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

    @Modifying
    @Query("UPDATE EmailVerificationToken e SET e.verified = true "
            + "WHERE e.id = :id AND e.verified = false AND e.expiryDate > :now")
    int consumeIfValid(@Param("id") Long id, @Param("now") LocalDateTime now);

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

    @Query("SELECT e FROM EmailVerificationToken e WHERE LOWER(e.email) = LOWER(:email)")
    Optional<EmailVerificationToken> findByEmailIgnoreCase(@Param("email") String email);

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
