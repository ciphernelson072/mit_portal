package com.sis.repository;

import com.sis.entity.ServiceRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ServiceRequestRepository extends JpaRepository<ServiceRequest, Long> {
    List<ServiceRequest> findByStudentIdOrderByCreatedAtDesc(Long studentId);
    List<ServiceRequest> findByRequesterIdOrderByCreatedAtDesc(Long requesterId);
    List<ServiceRequest> findByStudentUserIdOrderByCreatedAtDesc(Long userId);
    @Query("select request from ServiceRequest request " +
            "where request.requester.id = :userId or request.student.user.id = :userId " +
            "order by request.createdAt desc")
    List<ServiceRequest> findVisibleToUserOrderByCreatedAtDesc(@Param("userId") Long userId);
    List<ServiceRequest> findAllByOrderByCreatedAtDesc();
}
