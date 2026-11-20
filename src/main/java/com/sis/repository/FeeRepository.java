package com.sis.repository;

import com.sis.entity.Fee;
import com.sis.entity.FeeStatus;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.time.LocalDate;
import jakarta.persistence.LockModeType;

public interface FeeRepository extends JpaRepository<Fee, Long> {
    @EntityGraph(attributePaths = {"student", "student.user"})
    @Query("select fee from Fee fee")
    List<Fee> findAllWithStudentAndUser();
    List<Fee> findByStudentId(Long studentId);
    List<Fee> findByStudentIdAndStatus(Long studentId, FeeStatus status);
    @EntityGraph(attributePaths = {"student", "student.user"})
    @Query("select fee from Fee fee where fee.status = com.sis.entity.FeeStatus.ACTIVE "
            + "and fee.paid = false and fee.dueDate is not null "
            + "and fee.dueDate <= :latestDueDate order by fee.dueDate asc, fee.id asc")
    List<Fee> findUnpaidWithDueDateOnOrBefore(@Param("latestDueDate") LocalDate latestDueDate);
    long countByPaid(boolean paid);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select fee from Fee fee where fee.id = :id")
    Optional<Fee> findByIdForUpdate(@Param("id") Long id);
}