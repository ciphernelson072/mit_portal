package com.sis.repository;

import com.sis.entity.PaymentAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PaymentAllocationRepository extends JpaRepository<PaymentAllocation, Long> {
    List<PaymentAllocation> findByFeeIdOrderByAllocatedAtAsc(Long feeId);
}