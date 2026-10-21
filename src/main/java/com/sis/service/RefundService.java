package com.sis.service;

import com.sis.dto.CreateRefundRequest;
import com.sis.entity.*;
import com.sis.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@Transactional
public class RefundService {
    private final PaymentRefundRepository refunds;
    private final PaymentAttemptRepository attempts;
    private final PaymentAllocationRepository allocations;
    private final FeeRepository fees;
    private final AuditLogRepository auditLogs;
    private final UserRepository users;

    public RefundService(PaymentRefundRepository refunds, PaymentAttemptRepository attempts,
                         PaymentAllocationRepository allocations, FeeRepository fees,
                         AuditLogRepository auditLogs, UserRepository users) {
        this.refunds = refunds;
        this.attempts = attempts;
        this.allocations = allocations;
        this.fees = fees;
        this.auditLogs = auditLogs;
        this.users = users;
    }

    public PaymentRefund refund(CreateRefundRequest request, String processedBy) {
        PaymentAttempt attempt = attempts.findByIdForUpdate(request.getPaymentAttemptId())
                .filter(item -> item.getStatus() == PaymentAttemptStatus.COMPLETED)
                .orElseThrow(() -> new IllegalStateException("Only a completed payment can be refunded"));
        Fee fee = fees.findByIdForUpdate(attempt.getFeeId())
                .orElseThrow(() -> new IllegalArgumentException("Fee not found"));
        BigDecimal alreadyRefunded = refunds.findByPaymentAttemptIdAndStatus(attempt.getId(), RefundStatus.COMPLETED).stream()
                .map(PaymentRefund::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (alreadyRefunded.add(request.getAmount()).compareTo(attempt.getAmount()) > 0) {
            throw new IllegalStateException("Refund exceeds the payment amount");
        }
        PaymentRefund refund = new PaymentRefund();
        refund.setPaymentAttemptId(attempt.getId());
        refund.setFeeId(attempt.getFeeId());
        refund.setStudentId(attempt.getStudentId());
        refund.setAmount(request.getAmount());
        refund.setReason(request.getReason());
        refund.setProcessedBy(processedBy);
        PaymentRefund saved = refunds.save(refund);

        BigDecimal paid = allocations.findByFeeIdOrderByAllocatedAtAsc(attempt.getFeeId()).stream()
                .map(PaymentAllocation::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal returned = refunds.findByFeeIdAndStatus(attempt.getFeeId(), RefundStatus.COMPLETED).stream()
                .map(PaymentRefund::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (paid.subtract(returned).compareTo(fee.getAmount()) < 0) {
            fee.setPaid(false);
            fee.setPaidDate(null);
            fees.save(fee);
        }
        Long userId = users.findByUsernameNormalized(processedBy)
            .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + processedBy))
            .getId();
        auditLogs.save(new AuditLog(userId, "FINANCE_PAYMENT_REFUNDED",
            "Refund #" + saved.getId() + " for payment attempt #" + attempt.getId() + " fee #"
                + attempt.getFeeId() + " amount " + saved.getAmount() + ": " + saved.getReason()));
        return saved;
    }
}