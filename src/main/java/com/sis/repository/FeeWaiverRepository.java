package com.sis.repository;

import com.sis.entity.FeeWaiver;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FeeWaiverRepository extends JpaRepository<FeeWaiver, Long> {
    List<FeeWaiver> findByFeeIdAndActiveTrueOrderByApprovedAtDesc(Long feeId);
}