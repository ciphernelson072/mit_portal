package com.sis.repository;

import com.sis.entity.HostelApplication;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;

public interface HostelApplicationRepository extends JpaRepository<HostelApplication, Long> {
    List<HostelApplication> findByStudentIdOrderByCreatedAtDesc(Long studentId);
    List<HostelApplication> findByStudentIdAndStatus(Long studentId, String status);
    List<HostelApplication> findByStatusOrderByCreatedAtAsc(String status);
    Optional<HostelApplication> findByIdAndStatus(Long id, String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select application from HostelApplication application where application.id = :id and application.status = :status")
    Optional<HostelApplication> findByIdAndStatusForUpdate(@Param("id") Long id, @Param("status") String status);
}
