package com.sis.service;

import com.sis.dto.CreateRefundRequest;
import com.sis.entity.*;
import com.sis.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;

@Service
@Transactional
public class RefundService {
    private final PaymentRefundRepository refunds;
    private final PaymentAttemptRepository attempts;
    private final PaymentAllocationRepository allocations;
    private final PaymentTransactionRepository transactions;
    private final FeeWaiverRepository waivers;
    private final FeeRepository fees;
    private final AuditLogRepository auditLogs;
    private final UserRepository users;

    public RefundService(PaymentRefundRepository refunds, PaymentAttemptRepository attempts,
                         PaymentAllocationRepository allocations, PaymentTransactionRepository transactions, FeeRepository fees,
                         FeeWaiverRepository waivers, AuditLogRepository auditLogs, UserRepository users) {
        this.refunds = refunds;
        this.attempts = attempts;
        this.allocations = allocations;
        this.transactions = transactions;
        this.waivers = waivers;
        this.fees = fees;
        this.auditLogs = auditLogs;
        this.users = users;
    }

    public PaymentRefund refund(CreateRefundRequest request, String processedBy) {
        PaymentAttempt attempt = attempts.findByIdForUpdate(request.getPaymentAttemptId())
                .filter(item -> item.getStatus() == PaymentAttemptStatus.COMPLETED)
                .orElseThrow(() -> new IllegalStateException("Only a completed payment can be refunded"));
        fees.findByIdForUpdate(attempt.getFeeId())
                .orElseThrow(() -> new IllegalArgumentException("Fee not found"));
        BigDecimal alreadyRefunded = refunds.findByPaymentAttemptIdAndStatus(attempt.getId(), RefundStatus.COMPLETED).stream()
                .map(PaymentRefund::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        alreadyRefunded = alreadyRefunded.add(refunds.findByPaymentAttemptIdAndStatus(
                attempt.getId(), RefundStatus.PENDING).stream()
                .map(PaymentRefund::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
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

        Long userId = users.findByUsernameNormalized(processedBy)
            .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + processedBy))
            .getId();
        auditLogs.save(new AuditLog(userId, "FINANCE_REFUND_REQUESTED",
            "Refund #" + saved.getId() + " requested for payment attempt #" + attempt.getId() + " fee #"
                + attempt.getFeeId() + " amount " + saved.getAmount() + ": " + saved.getReason()));
        return saved;
    }

    public PaymentRefund complete(Long refundId, String reviewedBy, String refundReference) {
        String reference = refundReference == null ? "" : refundReference.trim();
        if (reference.isEmpty() || reference.length() > 120) {
            throw new IllegalArgumentException("A valid external refund reference is required");
        }

        PaymentRefund refund = findRefundForUpdate(refundId);
        if (refund.getStatus() == RefundStatus.COMPLETED) {
            if (reference.equalsIgnoreCase(refund.getRefundReference())) {
                return refund;
            }
            throw new IllegalStateException("Refund has already been completed with a different reference");
        }
        if (refund.getStatus() != RefundStatus.PENDING) {
            throw new IllegalStateException("Only pending refund requests can be reviewed");
        }
        requireDifferentReviewer(refund, reviewedBy);
        if (refunds.existsByRefundReferenceIgnoreCaseAndIdNot(reference, refundId)) {
            throw new IllegalStateException("This external refund reference has already been used");
        }

        refund.setStatus(RefundStatus.COMPLETED);
        refund.setReviewedBy(reviewedBy);
        refund.setReviewedAt(LocalDateTime.now());
        refund.setRefundReference(reference);
        PaymentRefund saved = refunds.save(refund);

        Fee fee = fees.findByIdForUpdate(refund.getFeeId())
                .orElseThrow(() -> new IllegalArgumentException("Fee not found"));
        BigDecimal paid = allocations.findByFeeIdOrderByAllocatedAtAsc(fee.getId()).stream()
                .map(PaymentAllocation::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal legacyPaid = transactions.findByFeeIdAndPaymentAttemptIdIsNullAndStatus(
                        fee.getId(), "COMPLETED").stream()
                .map(PaymentTransaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal returned = refunds.findByFeeIdAndStatus(fee.getId(), RefundStatus.COMPLETED).stream()
                .map(PaymentRefund::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal waived = waivers.findByFeeIdAndActiveTrueOrderByApprovedAtDesc(fee.getId()).stream()
                .map(FeeWaiver::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean fullyPaid = paid.add(legacyPaid).subtract(returned).add(waived)
                .compareTo(fee.getAmount()) >= 0;
        fee.setPaid(fullyPaid);
        fee.setPaidDate(fullyPaid ? (fee.getPaidDate() == null ? LocalDate.now() : fee.getPaidDate()) : null);
        fees.save(fee);

        audit(reviewedBy, "FINANCE_REFUND_COMPLETED",
                "Refund #" + saved.getId() + " completed for payment attempt #" + saved.getPaymentAttemptId()
                        + " fee #" + saved.getFeeId() + " amount " + saved.getAmount()
                        + " external reference " + reference);
        return saved;
    }

    public PaymentRefund reject(Long refundId, String reviewedBy, String reason) {
        PaymentRefund refund = findPendingRefundForUpdate(refundId);
        requireDifferentReviewer(refund, reviewedBy);
        String note = reason == null ? "" : reason.trim();
        if (note.isEmpty() || note.length() > 1000) {
            throw new IllegalArgumentException("A rejection reason of at most 1000 characters is required");
        }
        refund.setStatus(RefundStatus.REJECTED);
        refund.setReviewedBy(reviewedBy);
        refund.setReviewedAt(LocalDateTime.now());
        refund.setReviewNote(note);
        PaymentRefund saved = refunds.save(refund);
        audit(reviewedBy, "FINANCE_REFUND_REJECTED",
                "Refund #" + saved.getId() + " rejected for payment attempt #" + saved.getPaymentAttemptId()
                        + ": " + note);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<PaymentRefund> pending() {
        return refunds.findByStatusOrderByCreatedAtAsc(RefundStatus.PENDING);
    }

    private PaymentRefund findPendingRefundForUpdate(Long refundId) {
        PaymentRefund refund = findRefundForUpdate(refundId);
        if (refund.getStatus() != RefundStatus.PENDING) {
            throw new IllegalStateException("Only pending refund requests can be reviewed");
        }
        return refund;
    }

    private PaymentRefund findRefundForUpdate(Long refundId) {
        PaymentRefund existing = refunds.findById(refundId)
                .orElseThrow(() -> new IllegalArgumentException("Refund not found"));
        attempts.findByIdForUpdate(existing.getPaymentAttemptId())
                .orElseThrow(() -> new IllegalArgumentException("Payment attempt not found"));
        return refunds.findByIdForUpdate(refundId)
                .orElseThrow(() -> new IllegalArgumentException("Refund not found"));
    }

    private void requireDifferentReviewer(PaymentRefund refund, String reviewer) {
        if (refund.getProcessedBy() != null && reviewer != null
                && refund.getProcessedBy().equalsIgnoreCase(reviewer.trim())) {
            throw new IllegalStateException("A different finance user must review this refund request");
        }
    }

    private void audit(String username, String action, String details) {
        Long userId = users.findByUsernameNormalized(username)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + username))
                .getId();
        auditLogs.save(new AuditLog(userId, action, details));
    }
}