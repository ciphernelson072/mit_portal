package com.sis.repository;

import com.sis.entity.PaymentRefund;
import com.sis.entity.RefundStatus;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;

public interface PaymentRefundRepository extends JpaRepository<PaymentRefund, Long> {
    List<PaymentRefund> findByPaymentAttemptIdAndStatus(Long paymentAttemptId, RefundStatus status);
    List<PaymentRefund> findByFeeIdAndStatus(Long feeId, RefundStatus status);
    List<PaymentRefund> findByStatusOrderByCreatedAtAsc(RefundStatus status);
    boolean existsByRefundReferenceIgnoreCaseAndIdNot(String refundReference, Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select refund from PaymentRefund refund where refund.id = :id")
    Optional<PaymentRefund> findByIdForUpdate(@Param("id") Long id);
}