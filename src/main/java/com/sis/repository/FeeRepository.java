package com.sis.repository;

import com.sis.entity.Fee;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import javax.persistence.LockModeType;

public interface FeeRepository extends JpaRepository<Fee, Long> {
    List<Fee> findByStudentId(Long studentId);
    long countByPaid(boolean paid);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select fee from Fee fee where fee.id = :id")
    Optional<Fee> findByIdForUpdate(@Param("id") Long id);
}