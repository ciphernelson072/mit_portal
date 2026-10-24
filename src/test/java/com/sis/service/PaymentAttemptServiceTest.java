package com.sis.service;

import com.sis.entity.Fee;
import com.sis.entity.NotificationCategory;
import com.sis.entity.PaymentAllocation;
import com.sis.entity.PaymentAttempt;
import com.sis.entity.PaymentAttemptStatus;
import com.sis.entity.PaymentChannel;
import com.sis.entity.PaymentTransaction;
import com.sis.entity.PaymentRefund;
import com.sis.entity.Student;
import com.sis.entity.User;
import com.sis.dto.CreatePaymentAttemptRequest;
import com.sis.dto.PaymentDestinationView;
import com.sis.repository.AuditLogRepository;
import com.sis.repository.FeeRepository;
import com.sis.repository.FeeWaiverRepository;
import com.sis.repository.PaymentAllocationRepository;
import com.sis.repository.PaymentAttemptRepository;
import com.sis.repository.PaymentRefundRepository;
import com.sis.repository.PaymentTransactionRepository;
import com.sis.repository.UserRepository;
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
import org.mockito.ArgumentCaptor;

class PaymentAttemptServiceTest {

    @Test
    void confirmsPaymentAndOnlyNotifiesOnceForRepeatedConfirmation() {
        PaymentAttemptRepository attempts = mock(PaymentAttemptRepository.class);
        FeeRepository fees = mock(FeeRepository.class);
        PaymentAllocationRepository allocations = mock(PaymentAllocationRepository.class);
        FeeWaiverRepository waivers = mock(FeeWaiverRepository.class);
        AuditLogRepository auditLogs = mock(AuditLogRepository.class);
        UserRepository users = mock(UserRepository.class);
        PaymentTransactionRepository transactions = mock(PaymentTransactionRepository.class);
        PaymentDestinationService destinations = mock(PaymentDestinationService.class);
        NotificationService notifications = mock(NotificationService.class);
        PaymentRefundRepository refunds = mock(PaymentRefundRepository.class);

        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setId(18L);
        attempt.setFeeId(25L);
        attempt.setStudentId(14L);
        attempt.setAmount(new BigDecimal("25000.00"));
        attempt.setProviderReference("PAY-18");
        attempt.setStatus(PaymentAttemptStatus.PENDING);
        when(attempts.findByIdForUpdate(18L)).thenReturn(Optional.of(attempt));

        User user = new User();
        user.setId(42L);
        Student student = new Student();
        student.setUser(user);
        Fee fee = new Fee();
        fee.setId(25L);
        fee.setStudent(student);
        fee.setAmount(new BigDecimal("100000.00"));
        when(fees.findByIdForUpdate(25L)).thenReturn(Optional.of(fee));
        when(allocations.findByFeeIdOrderByAllocatedAtAsc(25L)).thenReturn(List.of());
        when(attempts.sumAmountByFeeIdAndStatus(25L, PaymentAttemptStatus.PENDING))
                .thenReturn(BigDecimal.ZERO);
        when(waivers.findByFeeIdAndActiveTrueOrderByApprovedAtDesc(25L)).thenReturn(List.of());
        when(attempts.save(any(PaymentAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentAttemptService service = new PaymentAttemptService(
                attempts, fees, allocations, waivers, auditLogs, users, transactions, destinations, notifications, refunds);

        service.confirm(18L);
        service.confirm(18L);

        verify(notifications).create(42L, "Payment confirmed",
                "Your payment of UGX 25000.00 for fee #25 has been confirmed.",
                NotificationCategory.PAYMENT);
        ArgumentCaptor<PaymentTransaction> transactionCaptor = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(transactions).save(transactionCaptor.capture());
        assertThat(transactionCaptor.getValue().getPaymentAttemptId()).isEqualTo(18L);
        assertThat(transactionCaptor.getValue().getProviderReference()).isEqualTo("PAY-18");
    }

    @Test
    void rejectsPaymentAttemptThatWouldExceedLockedFeeBalance() {
        PaymentAttemptRepository attempts = mock(PaymentAttemptRepository.class);
        FeeRepository fees = mock(FeeRepository.class);
        PaymentAllocationRepository allocations = mock(PaymentAllocationRepository.class);
        FeeWaiverRepository waivers = mock(FeeWaiverRepository.class);
        AuditLogRepository auditLogs = mock(AuditLogRepository.class);
        UserRepository users = mock(UserRepository.class);
        PaymentTransactionRepository transactions = mock(PaymentTransactionRepository.class);
        PaymentDestinationService destinations = mock(PaymentDestinationService.class);
        NotificationService notifications = mock(NotificationService.class);
        PaymentRefundRepository refunds = mock(PaymentRefundRepository.class);

        User user = new User();
        user.setId(42L);
        Student student = new Student();
        student.setId(14L);
        student.setUser(user);
        Fee fee = new Fee();
        fee.setId(25L);
        fee.setStudent(student);
        fee.setAmount(new BigDecimal("100000.00"));
        when(fees.findByIdForUpdate(25L)).thenReturn(Optional.of(fee));
        when(destinations.list()).thenReturn(List.of(new PaymentDestinationView(
                PaymentChannel.BANK_TRANSFER, "Bank transfer", true, "Account", "Transfer instructions")));
        when(allocations.findByFeeIdOrderByAllocatedAtAsc(25L))
                .thenReturn(List.of(allocation(new BigDecimal("50000.00"))));
        when(attempts.sumAmountByFeeIdAndStatus(25L, PaymentAttemptStatus.PENDING))
                .thenReturn(new BigDecimal("30000.00"));
        when(waivers.findByFeeIdAndActiveTrueOrderByApprovedAtDesc(25L)).thenReturn(List.of());

        PaymentAttemptService service = new PaymentAttemptService(
                attempts, fees, allocations, waivers, auditLogs, users, transactions, destinations, notifications, refunds);
        CreatePaymentAttemptRequest request = new CreatePaymentAttemptRequest();
        request.setFeeId(25L);
        request.setAmount(new BigDecimal("25000.00"));
        request.setPaymentChannel(PaymentChannel.BANK_TRANSFER);
        request.setProviderReference("PAY-NEW");

        assertThatThrownBy(() -> service.create(request, 14L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Payment amount exceeds the outstanding fee balance");
        verify(fees).findByIdForUpdate(25L);
        verify(attempts, never()).save(any(PaymentAttempt.class));
    }

    @Test
    void preventsManualConfirmationOfFlutterwaveAttempts() {
        PaymentAttemptRepository attempts = mock(PaymentAttemptRepository.class);
        FeeRepository fees = mock(FeeRepository.class);
        PaymentAllocationRepository allocations = mock(PaymentAllocationRepository.class);
        FeeWaiverRepository waivers = mock(FeeWaiverRepository.class);
        AuditLogRepository auditLogs = mock(AuditLogRepository.class);
        UserRepository users = mock(UserRepository.class);
        PaymentTransactionRepository transactions = mock(PaymentTransactionRepository.class);
        PaymentDestinationService destinations = mock(PaymentDestinationService.class);
        NotificationService notifications = mock(NotificationService.class);
        PaymentRefundRepository refunds = mock(PaymentRefundRepository.class);

        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setId(91L);
        attempt.setPaymentChannel(PaymentChannel.FLUTTERWAVE);
        attempt.setStatus(PaymentAttemptStatus.PENDING);
        when(attempts.findByIdForUpdate(91L)).thenReturn(Optional.of(attempt));

        PaymentAttemptService service = new PaymentAttemptService(
                attempts, fees, allocations, waivers, auditLogs, users, transactions, destinations, notifications, refunds);

        assertThatThrownBy(() -> service.confirm(91L, "finance"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Flutterwave payments must be verified with Flutterwave before confirmation");
        verify(allocations, never()).save(any(PaymentAllocation.class));
        verify(notifications, never()).create(any(), any(), any(), any());
    }

    @Test
    void permitsPaymentForTheOutstandingBalanceAfterACompletedRefund() {
        PaymentAttemptRepository attempts = mock(PaymentAttemptRepository.class);
        FeeRepository fees = mock(FeeRepository.class);
        PaymentAllocationRepository allocations = mock(PaymentAllocationRepository.class);
        FeeWaiverRepository waivers = mock(FeeWaiverRepository.class);
        AuditLogRepository auditLogs = mock(AuditLogRepository.class);
        UserRepository users = mock(UserRepository.class);
        PaymentTransactionRepository transactions = mock(PaymentTransactionRepository.class);
        PaymentDestinationService destinations = mock(PaymentDestinationService.class);
        NotificationService notifications = mock(NotificationService.class);
        PaymentRefundRepository refunds = mock(PaymentRefundRepository.class);

        User user = new User();
        user.setId(42L);
        Student student = new Student();
        student.setId(14L);
        student.setUser(user);
        Fee fee = new Fee();
        fee.setId(25L);
        fee.setStudent(student);
        fee.setAmount(new BigDecimal("100.00"));
        when(fees.findByIdForUpdate(25L)).thenReturn(Optional.of(fee));
        when(destinations.list()).thenReturn(List.of(new PaymentDestinationView(
                PaymentChannel.BANK_TRANSFER, "Bank transfer", true, "Account", "Transfer instructions")));
        when(allocations.findByFeeIdOrderByAllocatedAtAsc(25L))
                .thenReturn(List.of(allocation(new BigDecimal("100.00"))));
        PaymentRefund completedRefund = new PaymentRefund();
        completedRefund.setAmount(new BigDecimal("40.00"));
        when(refunds.findByFeeIdAndStatus(25L, com.sis.entity.RefundStatus.COMPLETED))
                .thenReturn(List.of(completedRefund));
        when(attempts.sumAmountByFeeIdAndStatus(25L, PaymentAttemptStatus.PENDING))
                .thenReturn(BigDecimal.ZERO);
        when(waivers.findByFeeIdAndActiveTrueOrderByApprovedAtDesc(25L)).thenReturn(List.of());
        when(attempts.save(any(PaymentAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentAttemptService service = new PaymentAttemptService(
                attempts, fees, allocations, waivers, auditLogs, users, transactions, destinations, notifications, refunds);
        CreatePaymentAttemptRequest request = new CreatePaymentAttemptRequest();
        request.setFeeId(25L);
        request.setAmount(new BigDecimal("40.00"));
        request.setPaymentChannel(PaymentChannel.BANK_TRANSFER);
        request.setProviderReference("PAY-REFUND-BALANCE");

        PaymentAttempt created = service.create(request, 14L);

        assertThat(created.getAmount()).isEqualByComparingTo("40.00");
        verify(attempts).save(any(PaymentAttempt.class));
    }

    @Test
    void partialPaymentAfterRefundDoesNotLeaveFeeMarkedPaid() {
        PaymentAttemptRepository attempts = mock(PaymentAttemptRepository.class);
        FeeRepository fees = mock(FeeRepository.class);
        PaymentAllocationRepository allocations = mock(PaymentAllocationRepository.class);
        FeeWaiverRepository waivers = mock(FeeWaiverRepository.class);
        AuditLogRepository auditLogs = mock(AuditLogRepository.class);
        UserRepository users = mock(UserRepository.class);
        PaymentTransactionRepository transactions = mock(PaymentTransactionRepository.class);
        PaymentDestinationService destinations = mock(PaymentDestinationService.class);
        NotificationService notifications = mock(NotificationService.class);
        PaymentRefundRepository refunds = mock(PaymentRefundRepository.class);

        User user = new User();
        user.setId(42L);
        Student student = new Student();
        student.setUser(user);
        Fee fee = new Fee();
        fee.setId(25L);
        fee.setStudent(student);
        fee.setAmount(new BigDecimal("100.00"));
        fee.setPaid(true);
        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setId(18L);
        attempt.setFeeId(25L);
        attempt.setStudentId(14L);
        attempt.setAmount(new BigDecimal("20.00"));
        attempt.setProviderReference("PAY-PARTIAL-AFTER-REFUND");
        attempt.setPaymentChannel(PaymentChannel.BANK_TRANSFER);
        attempt.setStatus(PaymentAttemptStatus.PENDING);
        when(attempts.findByIdForUpdate(18L)).thenReturn(Optional.of(attempt));
        when(fees.findByIdForUpdate(25L)).thenReturn(Optional.of(fee));
        PaymentAllocation allocation = allocation(new BigDecimal("80.00"));
        when(allocations.findByFeeIdOrderByAllocatedAtAsc(25L)).thenReturn(List.of(allocation));
        PaymentRefund refund = new PaymentRefund();
        refund.setAmount(new BigDecimal("80.00"));
        when(refunds.findByFeeIdAndStatus(25L, com.sis.entity.RefundStatus.COMPLETED))
                .thenReturn(List.of(refund));
        when(waivers.findByFeeIdAndActiveTrueOrderByApprovedAtDesc(25L)).thenReturn(List.of());
        when(attempts.save(any(PaymentAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentAttemptService service = new PaymentAttemptService(
                attempts, fees, allocations, waivers, auditLogs, users, transactions, destinations, notifications, refunds);
        service.confirm(18L);

        assertThat(fee.isPaid()).isFalse();
        assertThat(fee.getPaidDate()).isNull();
        verify(fees).save(fee);
    }

    private PaymentAllocation allocation(BigDecimal amount) {
        PaymentAllocation allocation = new PaymentAllocation();
        allocation.setAmount(amount);
        return allocation;
    }
}
