package com.sis.service;

import com.sis.dto.CreatePaymentAttemptRequest;
import com.sis.dto.FlutterwaveCheckoutDetails;
import com.sis.entity.*;
import com.sis.repository.AuditLogRepository;
import com.sis.repository.FeeRepository;
import com.sis.repository.FeeWaiverRepository;
import com.sis.repository.PaymentAllocationRepository;
import com.sis.repository.PaymentAttemptRepository;
import com.sis.repository.PaymentRefundRepository;
import com.sis.repository.PaymentTransactionRepository;
import com.sis.repository.UserRepository;
import com.sis.entity.NotificationCategory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class PaymentAttemptService {
    private final PaymentAttemptRepository attempts;
    private final PaymentTransactionRepository transactions;
    private final PaymentRefundRepository refunds;
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
                                 PaymentTransactionRepository transactions,
                                 PaymentDestinationService destinations,
                                 NotificationService notifications, PaymentRefundRepository refunds) {
        this.attempts = attempts;
        this.transactions = transactions;
        this.refunds = refunds;
        this.fees = fees;
        this.allocations = allocations;
        this.waivers = waivers;
        this.auditLogs = auditLogs;
        this.users = users;
        this.destinations = destinations;
        this.notifications = notifications;
    }

    public PaymentAttempt create(CreatePaymentAttemptRequest request, Long studentId) {
        if (request.getPaymentChannel() == PaymentChannel.FLUTTERWAVE) {
            throw new IllegalArgumentException("Use the Flutterwave hosted checkout to start this payment");
        }
        boolean channelAvailable = destinations.list().stream()
            .anyMatch(destination -> destination.getChannel() == request.getPaymentChannel() && destination.isAvailable());
        if (!channelAvailable) throw new IllegalStateException("Selected payment channel is not currently available");
        Fee fee = fees.findByIdForUpdate(request.getFeeId()).filter(item -> item.getStudent().getId().equals(studentId))
                .orElseThrow(() -> new IllegalArgumentException("Fee not found"));
        if (fee.isPaid()) throw new IllegalStateException("Fee is already paid");
        BigDecimal allocated = allocations.findByFeeIdOrderByAllocatedAtAsc(fee.getId()).stream()
                .map(PaymentAllocation::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        allocated = netAllocated(fee.getId(), allocated);
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

    public FlutterwaveCheckoutDetails createFlutterwaveAttempt(Long feeId, Long studentId) {
        boolean channelAvailable = destinations.list().stream()
                .anyMatch(destination -> destination.getChannel() == PaymentChannel.FLUTTERWAVE
                        && destination.isAvailable());
        if (!channelAvailable) {
            throw new IllegalStateException("Flutterwave checkout is not currently available");
        }

        Fee fee = fees.findByIdForUpdate(feeId)
                .filter(item -> item.getStudent().getId().equals(studentId))
                .orElseThrow(() -> new IllegalArgumentException("Fee not found"));
        if (fee.isPaid()) {
            throw new IllegalStateException("Fee is already paid");
        }
        LocalDateTime now = LocalDateTime.now();
        PaymentAttempt existing = attempts
                .findFirstByFeeIdAndStudentIdAndPaymentChannelAndStatusOrderByCreatedAtDesc(
                        feeId, studentId, PaymentChannel.FLUTTERWAVE, PaymentAttemptStatus.PENDING)
                .orElse(null);
        if (existing != null) {
            existing = attempts.findByIdForUpdate(existing.getId())
                    .orElseThrow(() -> new IllegalStateException("Pending payment attempt disappeared"));
            if (existing.getCheckoutUrl() != null && existing.getCheckoutExpiresAt() != null
                    && existing.getCheckoutExpiresAt().isAfter(now)) {
                return checkoutDetails(existing, fee, true);
            }
            throw new IllegalStateException(
                    "The previous Flutterwave checkout has expired. Ask finance to close it before starting a new checkout.");
        }

        BigDecimal allocated = allocations.findByFeeIdOrderByAllocatedAtAsc(feeId).stream()
                .map(PaymentAllocation::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        allocated = netAllocated(feeId, allocated);
        BigDecimal pending = attempts.sumAmountByFeeIdAndStatus(feeId, PaymentAttemptStatus.PENDING);
        BigDecimal waived = waivers.findByFeeIdAndActiveTrueOrderByApprovedAtDesc(feeId).stream()
                .map(FeeWaiver::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal amount = fee.getAmount().subtract(allocated).subtract(pending).subtract(waived);
        if (amount.signum() <= 0) {
            throw new IllegalStateException("There is no outstanding balance available for Flutterwave checkout");
        }

        Student student = fee.getStudent();
        User user = student.getUser();
        String email = user.getEmail();
        if (email == null || email.isBlank()) {
            email = student.getEmail();
        }
        if (email == null || email.isBlank()) {
            throw new IllegalStateException("Add a valid email address to your student profile before paying online");
        }

        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setFeeId(feeId);
        attempt.setStudentId(studentId);
        attempt.setAmount(amount);
        attempt.setPaymentChannel(PaymentChannel.FLUTTERWAVE);
        attempt.setProviderReference("MIT-FLW-" + UUID.randomUUID());
        attempt.setCheckoutExpiresAt(now.plusMinutes(60));
        PaymentAttempt saved = attempts.save(attempt);
        auditLogs.save(new AuditLog(user.getId(), "FINANCE_FLUTTERWAVE_CHECKOUT_STARTED",
                "Flutterwave checkout attempt #" + saved.getId() + " created for fee #" + feeId
                        + " amount " + amount));
        return checkoutDetails(saved, fee, false);
    }

    public PaymentAttempt recordFlutterwaveCheckout(Long attemptId, String checkoutUrl) {
        PaymentAttempt attempt = attempts.findByIdForUpdate(attemptId)
                .orElseThrow(() -> new IllegalArgumentException("Payment attempt not found"));
        if (attempt.getPaymentChannel() != PaymentChannel.FLUTTERWAVE
                || attempt.getStatus() != PaymentAttemptStatus.PENDING) {
            throw new IllegalStateException("Flutterwave checkout is no longer pending");
        }
        attempt.setCheckoutUrl(checkoutUrl);
        return attempts.save(attempt);
    }

    public void failFlutterwaveCheckoutCreation(Long attemptId) {
        PaymentAttempt attempt = attempts.findByIdForUpdate(attemptId)
                .orElseThrow(() -> new IllegalArgumentException("Payment attempt not found"));
        if (attempt.getPaymentChannel() == PaymentChannel.FLUTTERWAVE
                && attempt.getStatus() == PaymentAttemptStatus.PENDING) {
            attempt.setStatus(PaymentAttemptStatus.FAILED);
            attempt.setReviewedAt(LocalDateTime.now());
            attempt.setReviewNote("Flutterwave could not create the checkout session. Please retry.");
            attempts.save(attempt);
        }
    }

    public PaymentAttempt findFlutterwaveAttempt(String providerReference) {
        return attempts.findByProviderReference(providerReference)
                .filter(attempt -> attempt.getPaymentChannel() == PaymentChannel.FLUTTERWAVE)
                .orElseThrow(() -> new IllegalArgumentException("Flutterwave payment reference not found"));
    }

    public Optional<PaymentAttempt> findFlutterwaveAttemptIfPresent(String providerReference) {
        return attempts.findByProviderReferenceAndPaymentChannel(providerReference, PaymentChannel.FLUTTERWAVE);
    }

    public PaymentAttempt findStudentFlutterwaveAttempt(String providerReference, Long studentId) {
        return attempts.findByProviderReferenceAndStudentIdAndPaymentChannel(
                        providerReference, studentId, PaymentChannel.FLUTTERWAVE)
                .orElseThrow(() -> new IllegalArgumentException("Flutterwave payment reference not found"));
    }

    public PaymentAttempt confirmFlutterwave(Long attemptId, String transactionId) {
        if (transactionId == null || transactionId.isBlank()) {
            throw new IllegalArgumentException("Flutterwave transaction ID is required");
        }
        return confirmInternal(attemptId, null, transactionId);
    }

    public PaymentAttempt confirm(Long id) {
        return confirm(id, null);
    }

    public PaymentAttempt confirm(Long id, String confirmedBy) {
        return confirmInternal(id, confirmedBy, null);
    }

    private PaymentAttempt confirmInternal(Long id, String confirmedBy, String verifiedTransactionId) {
        PaymentAttempt attempt = attempts.findByIdForUpdate(id)
            .orElseThrow(() -> new IllegalArgumentException("Payment attempt not found"));
        if (attempt.getPaymentChannel() == PaymentChannel.FLUTTERWAVE && verifiedTransactionId == null) {
            throw new IllegalStateException("Flutterwave payments must be verified with Flutterwave before confirmation");
        }
        if (verifiedTransactionId != null && attempt.getPaymentChannel() != PaymentChannel.FLUTTERWAVE) {
            throw new IllegalStateException("Only Flutterwave payment attempts accept provider transaction IDs");
        }
        if (verifiedTransactionId != null && attempt.getProviderTransactionId() != null
                && !attempt.getProviderTransactionId().equals(verifiedTransactionId)) {
            throw new IllegalStateException("Payment attempt is already linked to another provider transaction");
        }
        if (attempt.getStatus() == PaymentAttemptStatus.COMPLETED) return attempt;
        if (attempt.getStatus() == PaymentAttemptStatus.FAILED) throw new IllegalStateException("Payment attempt has failed");
        Fee fee = fees.findByIdForUpdate(attempt.getFeeId())
                .orElseThrow(() -> new IllegalArgumentException("Fee not found"));
        BigDecimal allocated = allocations.findByFeeIdOrderByAllocatedAtAsc(fee.getId()).stream()
                .map(PaymentAllocation::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        allocated = netAllocated(fee.getId(), allocated);
        BigDecimal waived = waivers.findByFeeIdAndActiveTrueOrderByApprovedAtDesc(fee.getId()).stream()
            .map(FeeWaiver::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (allocated.add(attempt.getAmount()).add(waived).compareTo(fee.getAmount()) >= 0) {
            fee.setPaid(true);
            fee.setPaidDate(LocalDate.now());
            fees.save(fee);
        } else if (fee.isPaid()) {
            fee.setPaid(false);
            fee.setPaidDate(null);
            fees.save(fee);
        }
        attempt.setStatus(PaymentAttemptStatus.COMPLETED);
        attempt.setConfirmedAt(LocalDateTime.now());
        if (verifiedTransactionId != null) {
            attempt.setProviderTransactionId(verifiedTransactionId);
            attempt.setReviewNote("Transaction verified with Flutterwave.");
        }
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
        transactions.save(new PaymentTransaction(saved.getFeeId(), saved.getStudentId(), saved.getAmount(),
                saved.getId(), saved.getPaymentChannel().name(), saved.getProviderReference(),
                saved.getProviderTransactionId()));
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
        if (attempt.getPaymentChannel() == PaymentChannel.FLUTTERWAVE
                && (attempt.getCheckoutExpiresAt() == null
                    || attempt.getCheckoutExpiresAt().isAfter(LocalDateTime.now()))) {
            throw new IllegalStateException("An active Flutterwave checkout cannot be manually rejected");
        }
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
        if (attempt.getPaymentChannel() == PaymentChannel.FLUTTERWAVE) {
            throw new IllegalStateException("Flutterwave callbacks must use server-side transaction verification");
        }
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

    private FlutterwaveCheckoutDetails checkoutDetails(PaymentAttempt attempt, Fee fee, boolean existingCheckout) {
        Student student = fee.getStudent();
        User user = student.getUser();
        String email = user.getEmail() == null || user.getEmail().isBlank()
                ? student.getEmail() : user.getEmail();
        return new FlutterwaveCheckoutDetails(attempt.getId(), fee.getId(), attempt.getProviderReference(),
                attempt.getAmount(), email, student.getFullName(), student.getPhone(),
                attempt.getCheckoutUrl(), attempt.getCheckoutExpiresAt(), existingCheckout);
    }

    private BigDecimal netAllocated(Long feeId, BigDecimal allocated) {
        BigDecimal refunded = refunds.findByFeeIdAndStatus(feeId, RefundStatus.COMPLETED).stream()
                .map(PaymentRefund::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal net = allocated.subtract(refunded);
        return net.signum() < 0 ? BigDecimal.ZERO : net;
    }
}