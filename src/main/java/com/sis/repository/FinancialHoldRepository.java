package com.sis.repository;

import com.sis.entity.FinancialHold;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FinancialHoldRepository extends JpaRepository<FinancialHold, Long> {
    @EntityGraph(attributePaths = {"student.user"})
    List<FinancialHold> findByStudentIdAndActiveTrueOrderByCreatedAtDesc(Long studentId);
    boolean existsByStudentIdAndActiveTrue(Long studentId);
}