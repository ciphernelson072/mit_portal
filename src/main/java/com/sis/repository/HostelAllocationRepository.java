package com.sis.repository;

import com.sis.entity.HostelAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface HostelAllocationRepository extends JpaRepository<HostelAllocation, Long> {
    List<HostelAllocation> findByStudentIdOrderByAllocatedAtDesc(Long studentId);
}