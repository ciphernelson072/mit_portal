package com.sis.repository;

import com.sis.entity.TransportPass;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface TransportPassRepository extends JpaRepository<TransportPass, Long> {
    Optional<TransportPass> findFirstByStudentIdOrderByIdDesc(Long studentId);
    boolean existsByStudentIdAndStatus(Long studentId, String status);
}
