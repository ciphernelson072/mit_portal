package com.sis.repository;

import com.sis.entity.ProcurementRequest;
import com.sis.entity.ProcurementRequestStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ProcurementRequestRepository extends JpaRepository<ProcurementRequest, Long> {
    @EntityGraph(attributePaths = "supplier")
    List<ProcurementRequest> findByStatusOrderByCreatedAtAsc(ProcurementRequestStatus status);

    @EntityGraph(attributePaths = "supplier")
    @Query("select request from ProcurementRequest request where request.id = :id")
    Optional<ProcurementRequest> findByIdWithSupplier(@Param("id") Long id);
}