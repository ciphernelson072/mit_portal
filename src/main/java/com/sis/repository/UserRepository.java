package com.sis.repository;

import com.sis.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import com.sis.entity.Role;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);

    Optional<User> findByUsernameIgnoreCase(String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from User user where user.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update User user set user.credentialVersion = user.credentialVersion + 1 where user.id = :id")
    int incrementCredentialVersion(@Param("id") Long id);

    default Optional<User> findByUsernameNormalized(String username) {
        return username == null ? Optional.empty() : findByUsernameIgnoreCase(username.trim());
    }

    Optional<User> findByEmail(String email);

    Optional<User> findByEmailIgnoreCase(String email);

    default Optional<User> findByEmailNormalized(String email) {
        return email == null ? Optional.empty() : findByEmailIgnoreCase(email.trim());
    }

    List<User> findByIsActiveTrueOrderByIdAsc();
    List<User> findByIsActiveFalseOrderByIdAsc();
    List<User> findByRoleAndIsActiveTrue(Role role);
    List<User> findByRoleAndIsActiveTrueOrderByIdAsc(Role role);
    @Query("select user.id from User user where user.isActive = true and user.id <> :excludedUserId order by user.id")
    List<Long> findActiveUserIdsExcluding(@Param("excludedUserId") Long excludedUserId);
    long countByIsActiveTrue();
    long countByIsActiveFalse();
    long countByRoleAndIsActive(Role role, Boolean isActive);
}
