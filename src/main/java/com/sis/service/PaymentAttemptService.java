package com.sis.service;

import com.sis.dto.CreatePaymentAttemptRequest;
import com.sis.entity.*;
import com.sis.repository.AuditLogRepository;
import com.sis.repository.FeeRepository;
import com.sis.repository.FeeWaiverRepository;
import com.sis.repository.PaymentAllocationRepository;
import com.sis.repository.PaymentAttemptRepository;
import com.sis.repository.UserRepository;
import com.sis.entity.NotificationCategory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class PaymentAttemptService {
    private final PaymentAttemptRepository attempts;
    private final FeeRepository fees;
    private final PaymentAllocationRepository allocations;
    private final FeeWaiverRepository waivers;
    private final AuditLogRepository auditLogs;
    private final UserRepository users;
    private final PaymentDestinationService destinations;
    private final NotificationService notifications;

    public PaymentAttemptService(PaymentAttemptRepository attempts, FeeRepository fees,
                                 PaymentAllocationRepository allocations, FeeWaiverRepository waivers,
                                 AuditLogRepository auditLogs, UserRepository users,
                                 PaymentDestinationService destinations,
                                 NotificationService notifications) {
        this.attempts = attempts;
        this.fees = fees;
        this.allocations = allocations;
        this.waivers = waivers;
        this.auditLogs = auditLogs;
        this.users = users;
        this.destinations = destinations;
        this.notifications = notifications;
    }

    public PaymentAttempt create(CreatePaymentAttemptRequest request, Long studentId) {
        boolean channelAvailable = destinations.list().stream()
            .anyMatch(destination -> destination.getChannel() == request.getPaymentChannel() && destination.isAvailable());
        if (!channelAvailable) throw new IllegalStateException("Selected payment channel is not currently available");
        Fee fee = fees.findByIdForUpdate(request.getFeeId()).filter(item -> item.getStudent().getId().equals(studentId))
                .orElseThrow(() -> new IllegalArgumentException("Fee not found"));
        if (fee.isPaid()) throw new IllegalStateException("Fee is already paid");
        BigDecimal allocated = allocations.findByFeeIdOrderByAllocatedAtAsc(fee.getId()).stream()
                .map(PaymentAllocation::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pending = attempts.sumAmountByFeeIdAndStatus(fee.getId(), PaymentAttemptStatus.PENDING);
        BigDecimal waived = waivers.findByFeeIdAndActiveTrueOrderByApprovedAtDesc(fee.getId()).stream()
            .map(FeeWaiver::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (allocated.add(pending).add(request.getAmount()).add(waived).compareTo(fee.getAmount()) > 0) {
            throw new IllegalStateException("Payment amount exceeds the outstanding fee balance");
        }
        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setFeeId(fee.getId());
        attempt.setStudentId(studentId);
        attempt.setAmount(request.getAmount());
        attempt.setPaymentChannel(request.getPaymentChannel());
        attempt.setProviderReference(request.getProviderReference().trim());
        PaymentAttempt saved = attempts.save(attempt);
        auditLogs.save(new AuditLog(fee.getStudent().getUser().getId(), "FINANCE_PAYMENT_SUBMITTED",
            "Payment attempt #" + saved.getId() + " submitted for fee #" + saved.getFeeId()
                + " via " + saved.getPaymentChannel() + " for " + saved.getAmount()
                + " provider reference " + saved.getProviderReference()));
        return saved;
    }

    public PaymentAttempt confirm(Long id) {
        return confirm(id, null);
    }

    public PaymentAttempt confirm(Long id, String confirmedBy) {
        PaymentAttempt attempt = attempts.findByIdForUpdate(id)
            .orElseThrow(() -> new IllegalArgumentException("Payment attempt not found"));
        if (attempt.getStatus() == PaymentAttemptStatus.COMPLETED) return attempt;
        if (attempt.getStatus() == PaymentAttemptStatus.FAILED) throw new IllegalStateException("Payment attempt has failed");
        Fee fee = fees.findByIdForUpdate(attempt.getFeeId())
                .orElseThrow(() -> new IllegalArgumentException("Fee not found"));
        BigDecimal allocated = allocations.findByFeeIdOrderByAllocatedAtAsc(fee.getId()).stream()
                .map(PaymentAllocation::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal waived = waivers.findByFeeIdAndActiveTrueOrderByApprovedAtDesc(fee.getId()).stream()
            .map(FeeWaiver::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (allocated.add(attempt.getAmount()).add(waived).compareTo(fee.getAmount()) >= 0) {
            fee.setPaid(true);
            fee.setPaidDate(LocalDate.now());
            fees.save(fee);
        }
        attempt.setStatus(PaymentAttemptStatus.COMPLETED);
        attempt.setConfirmedAt(LocalDateTime.now());
        if (confirmedBy != null) {
            attempt.setReviewedBy(confirmedBy);
            attempt.setReviewedAt(LocalDateTime.now());
            attempt.setReviewNote("Verified against provider transaction");
        }
        PaymentAttempt saved = attempts.save(attempt);
        PaymentAllocation allocation = new PaymentAllocation();
        allocation.setPaymentAttemptId(saved.getId());
        allocation.setFeeId(saved.getFeeId());
        allocation.setAmount(saved.getAmount());
        allocations.save(allocation);
        if (confirmedBy != null) {
            Long userId = users.findByUsernameNormalized(confirmedBy)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + confirmedBy))
                .getId();
            auditLogs.save(new AuditLog(userId, "FINANCE_PAYMENT_CONFIRMED",
                "Payment attempt #" + saved.getId() + " provider reference " + saved.getProviderReference()
                    + " amount " + saved.getAmount() + " fee #" + saved.getFeeId()));
        }
        notifications.create(
                fee.getStudent().getUser().getId(),
                "Payment confirmed",
                "Your payment of UGX " + saved.getAmount() + " for fee #" + saved.getFeeId() + " has been confirmed.",
                NotificationCategory.PAYMENT);
        return saved;
    }

    public PaymentAttempt reject(Long id, String rejectedBy, String reason) {
        PaymentAttempt attempt = attempts.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("Payment attempt not found"));
        if (attempt.getStatus() != PaymentAttemptStatus.PENDING) {
            throw new IllegalStateException("Only pending payment attempts can be rejected");
        }
        String reviewNote = reason.trim();
        if (reviewNote.isEmpty() || reviewNote.length() > 500) {
            throw new IllegalArgumentException("A rejection reason of at most 500 characters is required");
        }
        attempt.setStatus(PaymentAttemptStatus.FAILED);
        attempt.setReviewedBy(rejectedBy);
        attempt.setReviewedAt(LocalDateTime.now());
        attempt.setReviewNote(reviewNote);
        PaymentAttempt saved = attempts.save(attempt);
        Long userId = users.findByUsernameNormalized(rejectedBy)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + rejectedBy))
                .getId();
        auditLogs.save(new AuditLog(userId, "FINANCE_PAYMENT_REJECTED",
                "Payment attempt #" + saved.getId() + " rejected for fee #" + saved.getFeeId()
                        + " provider reference " + saved.getProviderReference() + ": " + reviewNote));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<PaymentAttempt> pendingAttempts() {
        return attempts.findByStatusOrderByCreatedAtAsc(PaymentAttemptStatus.PENDING);
    }

    public PaymentAttempt handleProviderStatus(String providerReference, String status) {
        PaymentAttempt attempt = attempts.findByProviderReference(providerReference)
                .orElseThrow(() -> new IllegalArgumentException("Payment reference not found"));
        if ("SUCCESS".equalsIgnoreCase(status) || "COMPLETED".equalsIgnoreCase(status)) {
            return confirm(attempt.getId());
        }
        if ("FAILED".equalsIgnoreCase(status) || "CANCELLED".equalsIgnoreCase(status)) {
            attempt = attempts.findByIdForUpdate(attempt.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Payment attempt not found"));
            if (attempt.getStatus() == PaymentAttemptStatus.PENDING) {
                attempt.setStatus(PaymentAttemptStatus.FAILED);
                return attempts.save(attempt);
            }
            return attempt;
        }
        throw new IllegalArgumentException("Unsupported provider payment status");
    }

    @Transactional(readOnly = true)
    public List<PaymentAttempt> studentAttempts(Long studentId) { return attempts.findByStudentIdOrderByCreatedAtDesc(studentId); }
}