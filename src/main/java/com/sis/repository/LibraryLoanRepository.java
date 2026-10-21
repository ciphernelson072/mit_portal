package com.sis.repository;

import com.sis.entity.LibraryLoan;
import com.sis.entity.LibraryLoanStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LibraryLoanRepository extends JpaRepository<LibraryLoan, Long> {
    List<LibraryLoan> findByStudentIdOrderByBorrowedAtDesc(Long studentId);
    List<LibraryLoan> findByStatus(LibraryLoanStatus status);
}