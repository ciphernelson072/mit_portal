package com.sis.repository;

import com.sis.entity.MfaRecoveryCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface MfaRecoveryCodeRepository extends JpaRepository<MfaRecoveryCode, Long> {
    @Modifying
    @Query("update MfaRecoveryCode code set code.usedAt = :usedAt "
            + "where code.user.id = :userId and code.codeHash = :codeHash "
            + "and code.usedAt is null")
    int consume(@Param("userId") Long userId,
                @Param("codeHash") String codeHash,
                @Param("usedAt") LocalDateTime usedAt);

    @Modifying
    @Query("delete from MfaRecoveryCode code where code.user.id = :userId")
    int deleteByUserId(@Param("userId") Long userId);
}
