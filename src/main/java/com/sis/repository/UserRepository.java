package com.sis.repository;

import com.sis.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import com.sis.entity.Role;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);

    Optional<User> findByUsernameIgnoreCase(String username);

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
    @Query("select user.id from User user where user.isActive = true and user.id <> :excludedUserId order by user.id")
    List<Long> findActiveUserIdsExcluding(@Param("excludedUserId") Long excludedUserId);
    long countByIsActiveTrue();
    long countByIsActiveFalse();
    long countByRoleAndIsActive(Role role, Boolean isActive);
}
