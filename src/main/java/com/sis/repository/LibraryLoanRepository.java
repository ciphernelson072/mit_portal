package com.sis.repository;

import com.sis.entity.LibraryLoan;
import com.sis.entity.LibraryLoanStatus;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;

public interface LibraryLoanRepository extends JpaRepository<LibraryLoan, Long> {
    List<LibraryLoan> findByStudentIdOrderByBorrowedAtDesc(Long studentId);
    List<LibraryLoan> findByStatus(LibraryLoanStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select loan from LibraryLoan loan where loan.id = :id")
    Optional<LibraryLoan> findByIdForUpdate(@Param("id") Long id);
}