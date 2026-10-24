package com.sis.service;

import com.sis.dto.CreateFeeWaiverRequest;
import com.sis.entity.Fee;
import com.sis.entity.FeeWaiver;
import com.sis.entity.AuditLog;
import com.sis.entity.PaymentAllocation;
import com.sis.entity.PaymentAttemptStatus;
import com.sis.entity.PaymentRefund;
import com.sis.entity.RefundStatus;
import com.sis.repository.PaymentAttemptRepository;
import com.sis.repository.AuditLogRepository;
import com.sis.repository.FeeRepository;
import com.sis.repository.FeeWaiverRepository;
import com.sis.repository.PaymentAllocationRepository;
import com.sis.repository.UserRepository;
import com.sis.repository.PaymentRefundRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
@Transactional
public class FeeWaiverService {
    private final FeeWaiverRepository waivers;
    private final FeeRepository fees;
    private final PaymentAllocationRepository allocations;
    private final PaymentAttemptRepository attempts;
    private final AuditLogRepository auditLogs;
    private final UserRepository users;
    private final PaymentRefundRepository refunds;

    public FeeWaiverService(FeeWaiverRepository waivers, FeeRepository fees, PaymentAllocationRepository allocations,
                            PaymentAttemptRepository attempts, AuditLogRepository auditLogs, UserRepository users,
                            PaymentRefundRepository refunds) {
        this.waivers = waivers;
        this.fees = fees;
        this.allocations = allocations;
        this.attempts = attempts;
        this.auditLogs = auditLogs;
        this.users = users;
        this.refunds = refunds;
    }

    public FeeWaiver create(CreateFeeWaiverRequest request, String approvedBy) {
        Fee fee = fees.findByIdForUpdate(request.getFeeId())
                .orElseThrow(() -> new IllegalArgumentException("Fee not found"));
        if (fee.isPaid()) throw new IllegalStateException("Fee is already paid");
        BigDecimal current = waivers.findByFeeIdAndActiveTrueOrderByApprovedAtDesc(fee.getId()).stream()
                .map(FeeWaiver::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal allocated = allocations.findByFeeIdOrderByAllocatedAtAsc(fee.getId()).stream()
                .map(PaymentAllocation::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        allocated = netAllocated(fee.getId(), allocated);
        BigDecimal pending = attempts.sumAmountByFeeIdAndStatus(fee.getId(), PaymentAttemptStatus.PENDING);
        if (current.add(allocated).add(pending).add(request.getAmount()).compareTo(fee.getAmount()) > 0) {
            throw new IllegalStateException("Waiver amount exceeds the fee balance");
        }
        FeeWaiver waiver = new FeeWaiver();
        waiver.setFeeId(fee.getId());
        waiver.setAmount(request.getAmount());
        waiver.setReason(request.getReason());
        waiver.setApprovedBy(approvedBy);
        FeeWaiver saved = waivers.save(waiver);
        settleIfCovered(fee);
        Long userId = users.findByUsernameNormalized(approvedBy)
            .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + approvedBy))
            .getId();
        auditLogs.save(new AuditLog(userId, "FINANCE_FEE_WAIVER_APPROVED",
            "Waiver #" + saved.getId() + " approved for fee #" + saved.getFeeId() + " amount "
                + saved.getAmount() + ": " + saved.getReason()));
        return saved;
    }

    private void settleIfCovered(Fee fee) {
        BigDecimal waived = waivers.findByFeeIdAndActiveTrueOrderByApprovedAtDesc(fee.getId()).stream()
                .map(FeeWaiver::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paid = allocations.findByFeeIdOrderByAllocatedAtAsc(fee.getId()).stream()
                .map(item -> item.getAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        paid = netAllocated(fee.getId(), paid);
        if (waived.add(paid).compareTo(fee.getAmount()) >= 0) {
            fee.setPaid(true);
            fee.setPaidDate(LocalDate.now());
            fees.save(fee);
        }
    }

    private BigDecimal netAllocated(Long feeId, BigDecimal allocated) {
        BigDecimal refunded = refunds.findByFeeIdAndStatus(feeId, RefundStatus.COMPLETED).stream()
                .map(PaymentRefund::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal net = allocated.subtract(refunded);
        return net.signum() < 0 ? BigDecimal.ZERO : net;
    }
}