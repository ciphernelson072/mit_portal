package com.sis.repository;

import com.sis.entity.ServiceRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServiceRequestRepository extends JpaRepository<ServiceRequest, Long> {
    List<ServiceRequest> findByStudentIdOrderByCreatedAtDesc(Long studentId);
    List<ServiceRequest> findByRequesterIdOrderByCreatedAtDesc(Long requesterId);
    List<ServiceRequest> findByStudentUserIdOrderByCreatedAtDesc(Long userId);
    List<ServiceRequest> findAllByOrderByCreatedAtDesc();
}
