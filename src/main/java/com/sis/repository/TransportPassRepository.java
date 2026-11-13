package com.sis.repository;

import com.sis.entity.TransportPass;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.time.LocalDate;
import java.util.List;

public interface TransportPassRepository extends JpaRepository<TransportPass, Long> {
    Optional<TransportPass> findFirstByStudentIdOrderByIdDesc(Long studentId);
    boolean existsByStudentIdAndStatus(Long studentId, String status);
    boolean existsByStudentIdAndStatusAndValidUntilGreaterThanEqual(Long studentId, String status, LocalDate date);
    List<TransportPass> findByStudentIdAndStatusAndValidUntilBefore(Long studentId, String status, LocalDate date);
}
