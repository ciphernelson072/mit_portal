package com.sis.service;

import com.sis.dto.CreateRefundRequest;
import com.sis.entity.*;
import com.sis.repository.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefundServiceTest {

    @Test
    void createsPendingRequestWithoutClaimingMoneyWasReturned() {
        PaymentRefundRepository refunds = mock(PaymentRefundRepository.class);
        PaymentAttemptRepository attempts = mock(PaymentAttemptRepository.class);
        PaymentAllocationRepository allocations = mock(PaymentAllocationRepository.class);
        PaymentTransactionRepository transactions = mock(PaymentTransactionRepository.class);
        FeeWaiverRepository waivers = mock(FeeWaiverRepository.class);
        FeeRepository fees = mock(FeeRepository.class);
        AuditLogRepository auditLogs = mock(AuditLogRepository.class);
        UserRepository users = mock(UserRepository.class);
        PaymentAttempt attempt = completedAttempt();
        when(attempts.findByIdForUpdate(12L)).thenReturn(Optional.of(attempt));
        when(fees.findByIdForUpdate(5L)).thenReturn(Optional.of(fee()));
        when(users.findByUsernameNormalized("maker")).thenReturn(Optional.of(user(31L)));
        when(refunds.save(any(PaymentRefund.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RefundService service = new RefundService(refunds, attempts, allocations, transactions, fees, waivers, auditLogs, users);
        CreateRefundRequest request = new CreateRefundRequest();
        request.setPaymentAttemptId(12L);
        request.setAmount(new BigDecimal("40.00"));
        request.setReason("Duplicate payment");

        PaymentRefund saved = service.refund(request, "maker");

        assertThat(saved.getStatus()).isEqualTo(RefundStatus.PENDING);
        assertThat(saved.getProcessedBy()).isEqualTo("maker");
        verify(fees, never()).save(any(Fee.class));
        verify(auditLogs).save(any(AuditLog.class));
    }

    @Test
    void onlyDifferentReviewerCanCompleteRefundAndFeeBalanceIsReopened() {
        PaymentRefundRepository refunds = mock(PaymentRefundRepository.class);
        PaymentAttemptRepository attempts = mock(PaymentAttemptRepository.class);
        PaymentAllocationRepository allocations = mock(PaymentAllocationRepository.class);
        PaymentTransactionRepository transactions = mock(PaymentTransactionRepository.class);
        FeeWaiverRepository waivers = mock(FeeWaiverRepository.class);
        FeeRepository fees = mock(FeeRepository.class);
        AuditLogRepository auditLogs = mock(AuditLogRepository.class);
        UserRepository users = mock(UserRepository.class);
        PaymentAttempt attempt = completedAttempt();
        Fee fee = fee();
        PaymentRefund refund = pendingRefund();
        PaymentAllocation allocation = new PaymentAllocation();
        allocation.setAmount(new BigDecimal("100.00"));
        when(refunds.findById(44L)).thenReturn(Optional.of(refund));
        when(refunds.findByIdForUpdate(44L)).thenReturn(Optional.of(refund));
        when(attempts.findByIdForUpdate(12L)).thenReturn(Optional.of(attempt));
        when(fees.findByIdForUpdate(5L)).thenReturn(Optional.of(fee));
        when(allocations.findByFeeIdOrderByAllocatedAtAsc(5L)).thenReturn(List.of(allocation));
        when(transactions.findByFeeIdAndPaymentAttemptIdIsNullAndStatus(5L, "COMPLETED")).thenReturn(List.of());
        when(refunds.findByFeeIdAndStatus(5L, RefundStatus.COMPLETED)).thenReturn(List.of(refund));
        when(refunds.existsByRefundReferenceIgnoreCaseAndIdNot("BANK-REF-1", 44L)).thenReturn(false);
        when(refunds.save(any(PaymentRefund.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(users.findByUsernameNormalized("reviewer")).thenReturn(Optional.of(user(32L)));

        RefundService service = new RefundService(refunds, attempts, allocations, transactions, fees, waivers, auditLogs, users);

        assertThatThrownBy(() -> service.complete(44L, "maker", "BANK-REF-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("A different finance user must review this refund request");
        verify(refunds, never()).save(any(PaymentRefund.class));

        PaymentRefund completed = service.complete(44L, "reviewer", "BANK-REF-1");

        assertThat(completed.getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(completed.getReviewedBy()).isEqualTo("reviewer");
        assertThat(completed.getRefundReference()).isEqualTo("BANK-REF-1");
        assertThat(fee.isPaid()).isFalse();
        verify(fees).save(fee);
    }

    @Test
    void completedRefundKeepsFeeSettledWhenAnActiveWaiverCoversTheBalance() {
        PaymentRefundRepository refunds = mock(PaymentRefundRepository.class);
        PaymentAttemptRepository attempts = mock(PaymentAttemptRepository.class);
        PaymentAllocationRepository allocations = mock(PaymentAllocationRepository.class);
        PaymentTransactionRepository transactions = mock(PaymentTransactionRepository.class);
        FeeWaiverRepository waivers = mock(FeeWaiverRepository.class);
        FeeRepository fees = mock(FeeRepository.class);
        AuditLogRepository auditLogs = mock(AuditLogRepository.class);
        UserRepository users = mock(UserRepository.class);
        PaymentAttempt attempt = completedAttempt();
        Fee fee = fee();
        PaymentRefund refund = pendingRefund();
        PaymentAllocation allocation = new PaymentAllocation();
        allocation.setAmount(new BigDecimal("100.00"));
        FeeWaiver waiver = new FeeWaiver();
        waiver.setAmount(new BigDecimal("40.00"));
        when(refunds.findById(44L)).thenReturn(Optional.of(refund));
        when(refunds.findByIdForUpdate(44L)).thenReturn(Optional.of(refund));
        when(attempts.findByIdForUpdate(12L)).thenReturn(Optional.of(attempt));
        when(fees.findByIdForUpdate(5L)).thenReturn(Optional.of(fee));
        when(allocations.findByFeeIdOrderByAllocatedAtAsc(5L)).thenReturn(List.of(allocation));
        when(transactions.findByFeeIdAndPaymentAttemptIdIsNullAndStatus(5L, "COMPLETED")).thenReturn(List.of());
        when(waivers.findByFeeIdAndActiveTrueOrderByApprovedAtDesc(5L)).thenReturn(List.of(waiver));
        when(refunds.findByFeeIdAndStatus(5L, RefundStatus.COMPLETED)).thenReturn(List.of(refund));
        when(refunds.existsByRefundReferenceIgnoreCaseAndIdNot("BANK-REF-2", 44L)).thenReturn(false);
        when(refunds.save(any(PaymentRefund.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(users.findByUsernameNormalized("reviewer")).thenReturn(Optional.of(user(32L)));
        RefundService service = new RefundService(refunds, attempts, allocations, transactions, fees, waivers, auditLogs, users);

        service.complete(44L, "reviewer", "BANK-REF-2");

        assertThat(fee.isPaid()).isTrue();
    }

    @Test
    void completingAgainWithSameExternalReferenceIsIdempotent() {
        PaymentRefundRepository refunds = mock(PaymentRefundRepository.class);
        PaymentAttemptRepository attempts = mock(PaymentAttemptRepository.class);
        PaymentAllocationRepository allocations = mock(PaymentAllocationRepository.class);
        PaymentTransactionRepository transactions = mock(PaymentTransactionRepository.class);
        FeeWaiverRepository waivers = mock(FeeWaiverRepository.class);
        FeeRepository fees = mock(FeeRepository.class);
        AuditLogRepository auditLogs = mock(AuditLogRepository.class);
        UserRepository users = mock(UserRepository.class);
        PaymentAttempt attempt = completedAttempt();
        PaymentRefund refund = pendingRefund();
        refund.setStatus(RefundStatus.COMPLETED);
        refund.setRefundReference("BANK-REF-3");
        when(refunds.findById(44L)).thenReturn(Optional.of(refund));
        when(refunds.findByIdForUpdate(44L)).thenReturn(Optional.of(refund));
        when(attempts.findByIdForUpdate(12L)).thenReturn(Optional.of(attempt));
        RefundService service = new RefundService(refunds, attempts, allocations, transactions,
                fees, waivers, auditLogs, users);

        PaymentRefund result = service.complete(44L, "reviewer", " bank-ref-3 ");

        assertThat(result).isSameAs(refund);
        verify(refunds, never()).save(any(PaymentRefund.class));
        verify(fees, never()).save(any(Fee.class));
        verify(auditLogs, never()).save(any(AuditLog.class));
    }

    @Test
    void refusesAnExternalRefundReferenceAlreadyUsedByAnotherRefund() {
        PaymentRefundRepository refunds = mock(PaymentRefundRepository.class);
        PaymentAttemptRepository attempts = mock(PaymentAttemptRepository.class);
        PaymentAllocationRepository allocations = mock(PaymentAllocationRepository.class);
        PaymentTransactionRepository transactions = mock(PaymentTransactionRepository.class);
        FeeWaiverRepository waivers = mock(FeeWaiverRepository.class);
        FeeRepository fees = mock(FeeRepository.class);
        AuditLogRepository auditLogs = mock(AuditLogRepository.class);
        UserRepository users = mock(UserRepository.class);
        PaymentRefund refund = pendingRefund();
        when(refunds.findById(44L)).thenReturn(Optional.of(refund));
        when(refunds.findByIdForUpdate(44L)).thenReturn(Optional.of(refund));
        when(attempts.findByIdForUpdate(12L)).thenReturn(Optional.of(completedAttempt()));
        when(refunds.existsByRefundReferenceIgnoreCaseAndIdNot("BANK-REF-5", 44L)).thenReturn(true);
        when(users.findByUsernameNormalized("reviewer")).thenReturn(Optional.of(user(32L)));
        RefundService service = new RefundService(refunds, attempts, allocations, transactions,
                fees, waivers, auditLogs, users);

        assertThatThrownBy(() -> service.complete(44L, "reviewer", "BANK-REF-5"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("This external refund reference has already been used");
        verify(refunds, never()).save(any(PaymentRefund.class));
        verify(fees, never()).save(any(Fee.class));
    }

    @Test
    void completedRefundIncludesLegacyCashTransactionsWhenRecalculatingSettlement() {
        PaymentRefundRepository refunds = mock(PaymentRefundRepository.class);
        PaymentAttemptRepository attempts = mock(PaymentAttemptRepository.class);
        PaymentAllocationRepository allocations = mock(PaymentAllocationRepository.class);
        PaymentTransactionRepository transactions = mock(PaymentTransactionRepository.class);
        FeeWaiverRepository waivers = mock(FeeWaiverRepository.class);
        FeeRepository fees = mock(FeeRepository.class);
        AuditLogRepository auditLogs = mock(AuditLogRepository.class);
        UserRepository users = mock(UserRepository.class);
        PaymentAttempt attempt = completedAttempt();
        Fee fee = fee();
        fee.setAmount(new BigDecimal("110.00"));
        PaymentRefund refund = pendingRefund();
        PaymentAllocation allocation = new PaymentAllocation();
        allocation.setAmount(new BigDecimal("50.00"));
        PaymentTransaction legacy = new PaymentTransaction(5L, 9L, new BigDecimal("100.00"));
        when(refunds.findById(44L)).thenReturn(Optional.of(refund));
        when(refunds.findByIdForUpdate(44L)).thenReturn(Optional.of(refund));
        when(attempts.findByIdForUpdate(12L)).thenReturn(Optional.of(attempt));
        when(fees.findByIdForUpdate(5L)).thenReturn(Optional.of(fee));
        when(allocations.findByFeeIdOrderByAllocatedAtAsc(5L)).thenReturn(List.of(allocation));
        when(transactions.findByFeeIdAndPaymentAttemptIdIsNullAndStatus(5L, "COMPLETED"))
                .thenReturn(List.of(legacy));
        when(refunds.findByFeeIdAndStatus(5L, RefundStatus.COMPLETED)).thenReturn(List.of(refund));
        when(refunds.existsByRefundReferenceIgnoreCaseAndIdNot("BANK-REF-4", 44L)).thenReturn(false);
        when(refunds.save(any(PaymentRefund.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(users.findByUsernameNormalized("reviewer")).thenReturn(Optional.of(user(32L)));
        RefundService service = new RefundService(refunds, attempts, allocations, transactions,
                fees, waivers, auditLogs, users);

        service.complete(44L, "reviewer", "BANK-REF-4");

        assertThat(fee.isPaid()).isTrue();
    }

    private PaymentAttempt completedAttempt() {
        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setId(12L);
        attempt.setFeeId(5L);
        attempt.setStudentId(9L);
        attempt.setAmount(new BigDecimal("100.00"));
        attempt.setStatus(PaymentAttemptStatus.COMPLETED);
        return attempt;
    }

    private PaymentRefund pendingRefund() {
        PaymentRefund refund = new PaymentRefund();
        refund.setId(44L);
        refund.setPaymentAttemptId(12L);
        refund.setFeeId(5L);
        refund.setStudentId(9L);
        refund.setAmount(new BigDecimal("40.00"));
        refund.setReason("Duplicate payment");
        refund.setProcessedBy("maker");
        refund.setStatus(RefundStatus.PENDING);
        return refund;
    }

    private Fee fee() {
        Fee fee = new Fee();
        fee.setId(5L);
        fee.setAmount(new BigDecimal("100.00"));
        fee.setPaid(true);
        return fee;
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }
}
