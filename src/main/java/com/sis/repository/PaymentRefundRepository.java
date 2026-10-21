package com.sis.repository;

import com.sis.entity.PaymentRefund;
import com.sis.entity.RefundStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PaymentRefundRepository extends JpaRepository<PaymentRefund, Long> {
    List<PaymentRefund> findByPaymentAttemptIdAndStatus(Long paymentAttemptId, RefundStatus status);
    List<PaymentRefund> findByFeeIdAndStatus(Long feeId, RefundStatus status);
}