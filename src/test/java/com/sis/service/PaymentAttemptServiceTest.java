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
import com.sis.dto.FlutterwaveCheckoutDetails;
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
    void lateFlutterwaveSuccessIsPersistedAndNotifiesFinanceOnlyOnce() {
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
        NotificationRecipientService notificationRecipients = mock(NotificationRecipientService.class);

        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setId(93L);
        attempt.setFeeId(25L);
        attempt.setStudentId(14L);
        attempt.setAmount(new BigDecimal("35000.00"));
        attempt.setProviderReference("MIT-FLW-late-payment");
        attempt.setPaymentChannel(PaymentChannel.FLUTTERWAVE);
        attempt.setStatus(PaymentAttemptStatus.FAILED);
        when(attempts.findByIdForUpdate(93L)).thenReturn(Optional.of(attempt));
        when(attempts.save(attempt)).thenReturn(attempt);
        User financeUser = new User();
        financeUser.setId(17L);
        when(notificationRecipients.findActiveUsersWithPermission(
                "FINANCE_PAYMENT_CONFIRM", com.sis.entity.Role.ADMIN))
                .thenReturn(List.of(financeUser));

        PaymentAttemptService service = new PaymentAttemptService(
                attempts, fees, allocations, waivers, auditLogs, users, transactions,
                destinations, notifications, refunds, notificationRecipients);

        service.flagLateFlutterwaveReconciliation(93L, "123456");
        service.flagLateFlutterwaveReconciliation(93L, "123456");

        assertThat(attempt.getProviderTransactionId()).isEqualTo("123456");
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.FAILED);
        assertThat(attempt.getReviewNote()).contains("Finance review required");
        verify(attempts).save(attempt);
        verify(notifications).createAutomated(
                "flutterwave-late-payment:93:123456:17", 17L,
                "Flutterwave payment needs reconciliation",
                "Verified successful transaction 123456 arrived after payment attempt #93 was closed. "
                        + "Reference MIT-FLW-late-payment, amount UGX 35000.00. "
                        + "No fee balance was changed; review the provider settlement before confirming.",
                NotificationCategory.PAYMENT);
        verify(fees, never()).findByIdForUpdate(org.mockito.ArgumentMatchers.anyLong());
        verify(allocations, never()).save(any(PaymentAllocation.class));
        verify(transactions, never()).save(any(PaymentTransaction.class));
    }

    @Test
    void lateFlutterwaveFlagDoesNotReplaceAnotherTransactionId() {
        PaymentAttemptRepository attempts = mock(PaymentAttemptRepository.class);
        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setId(94L);
        attempt.setPaymentChannel(PaymentChannel.FLUTTERWAVE);
        attempt.setStatus(PaymentAttemptStatus.FAILED);
        attempt.setProviderTransactionId("123456");
        attempt.setReviewNote("Verified successful Flutterwave payment arrived after attempt closure. Finance review required.");
        when(attempts.findByIdForUpdate(94L)).thenReturn(Optional.of(attempt));

        PaymentAttemptService service = new PaymentAttemptService(
                attempts, mock(FeeRepository.class), mock(PaymentAllocationRepository.class),
                mock(FeeWaiverRepository.class), mock(AuditLogRepository.class), mock(UserRepository.class),
                mock(PaymentTransactionRepository.class), mock(PaymentDestinationService.class),
                mock(NotificationService.class), mock(PaymentRefundRepository.class),
                mock(NotificationRecipientService.class));

        assertThatThrownBy(() -> service.flagLateFlutterwaveReconciliation(94L, "654321"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Payment attempt is already linked to another provider transaction");
        verify(attempts, never()).save(any(PaymentAttempt.class));
    }

    @Test
    void financeReconciliationQueueIncludesOnlyVerifiedLateFlutterwaveAttempts() {
        PaymentAttemptRepository attempts = mock(PaymentAttemptRepository.class);
        PaymentAttempt flagged = new PaymentAttempt();
        flagged.setId(97L);
        flagged.setPaymentChannel(PaymentChannel.FLUTTERWAVE);
        flagged.setStatus(PaymentAttemptStatus.FAILED);
        flagged.setProviderTransactionId("123459");
        flagged.setReviewNote("Verified successful Flutterwave payment arrived after attempt closure. Finance review required.");
        PaymentAttempt failedCheckout = new PaymentAttempt();
        failedCheckout.setId(98L);
        failedCheckout.setPaymentChannel(PaymentChannel.FLUTTERWAVE);
        failedCheckout.setStatus(PaymentAttemptStatus.FAILED);
        failedCheckout.setReviewNote("Flutterwave could not create the checkout session. Please retry.");
        when(attempts.findByPaymentChannelAndStatusAndProviderTransactionIdIsNotNullOrderByCreatedAtAsc(
                PaymentChannel.FLUTTERWAVE, PaymentAttemptStatus.FAILED))
                .thenReturn(List.of(flagged, failedCheckout));

        PaymentAttemptService service = new PaymentAttemptService(
                attempts, mock(FeeRepository.class), mock(PaymentAllocationRepository.class),
                mock(FeeWaiverRepository.class), mock(AuditLogRepository.class), mock(UserRepository.class),
                mock(PaymentTransactionRepository.class), mock(PaymentDestinationService.class),
                mock(NotificationService.class), mock(PaymentRefundRepository.class),
                mock(NotificationRecipientService.class));

        assertThat(service.flutterwaveReconciliationAttempts()).containsExactly(flagged);
    }

    @Test
    void financeCanConfirmLateFlutterwavePaymentOnlyThroughReconciliationAction() {
        PaymentAttemptRepository attempts = mock(PaymentAttemptRepository.class);
        FeeRepository fees = mock(FeeRepository.class);
        PaymentAllocationRepository allocations = mock(PaymentAllocationRepository.class);
        FeeWaiverRepository waivers = mock(FeeWaiverRepository.class);
        AuditLogRepository auditLogs = mock(AuditLogRepository.class);
        UserRepository users = mock(UserRepository.class);
        PaymentTransactionRepository transactions = mock(PaymentTransactionRepository.class);
        NotificationService notifications = mock(NotificationService.class);

        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setId(95L);
        attempt.setFeeId(25L);
        attempt.setStudentId(14L);
        attempt.setAmount(new BigDecimal("35000.00"));
        attempt.setProviderReference("MIT-FLW-late-confirm");
        attempt.setPaymentChannel(PaymentChannel.FLUTTERWAVE);
        attempt.setStatus(PaymentAttemptStatus.FAILED);
        attempt.setProviderTransactionId("123457");
        attempt.setReviewNote("Verified successful Flutterwave payment arrived after attempt closure. Finance review required.");
        when(attempts.findByIdForUpdate(95L)).thenReturn(Optional.of(attempt));
        User studentUser = new User();
        studentUser.setId(42L);
        Student student = new Student();
        student.setUser(studentUser);
        Fee fee = new Fee();
        fee.setId(25L);
        fee.setStudent(student);
        fee.setAmount(new BigDecimal("35000.00"));
        when(fees.findByIdForUpdate(25L)).thenReturn(Optional.of(fee));
        when(allocations.findByFeeIdOrderByAllocatedAtAsc(25L)).thenReturn(List.of());
        when(waivers.findByFeeIdAndActiveTrueOrderByApprovedAtDesc(25L)).thenReturn(List.of());
        User reviewer = new User();
        reviewer.setId(18L);
        when(users.findByUsernameNormalized("finance")).thenReturn(Optional.of(reviewer));
        when(attempts.save(attempt)).thenReturn(attempt);

        PaymentAttemptService service = new PaymentAttemptService(
                attempts, fees, allocations, waivers, auditLogs, users, transactions,
                mock(PaymentDestinationService.class), notifications, mock(PaymentRefundRepository.class),
                mock(NotificationRecipientService.class));

        assertThatThrownBy(() -> service.confirm(95L, "finance"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Flutterwave payments must be verified with Flutterwave before confirmation");
        PaymentAttempt completed = service.confirmLateFlutterwaveReconciliation(95L, "finance", "123457");

        assertThat(completed.getStatus()).isEqualTo(PaymentAttemptStatus.COMPLETED);
        assertThat(fee.isPaid()).isTrue();
        assertThat(attempt.getReviewedBy()).isEqualTo("finance");
        verify(allocations).save(any(PaymentAllocation.class));
        verify(transactions).save(any(PaymentTransaction.class));
        verify(auditLogs).save(any(com.sis.entity.AuditLog.class));
    }

    @Test
    void lateFlutterwaveConfirmationCannotExceedCurrentFeeBalance() {
        PaymentAttemptRepository attempts = mock(PaymentAttemptRepository.class);
        FeeRepository fees = mock(FeeRepository.class);
        PaymentAllocationRepository allocations = mock(PaymentAllocationRepository.class);
        FeeWaiverRepository waivers = mock(FeeWaiverRepository.class);
        AuditLogRepository auditLogs = mock(AuditLogRepository.class);
        PaymentTransactionRepository transactions = mock(PaymentTransactionRepository.class);
        PaymentRefundRepository refunds = mock(PaymentRefundRepository.class);

        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setId(96L);
        attempt.setFeeId(25L);
        attempt.setAmount(new BigDecimal("35000.00"));
        attempt.setPaymentChannel(PaymentChannel.FLUTTERWAVE);
        attempt.setStatus(PaymentAttemptStatus.FAILED);
        attempt.setProviderTransactionId("123458");
        attempt.setReviewNote("Verified successful Flutterwave payment arrived after attempt closure. Finance review required.");
        when(attempts.findByIdForUpdate(96L)).thenReturn(Optional.of(attempt));
        Fee fee = new Fee();
        fee.setId(25L);
        fee.setAmount(new BigDecimal("35000.00"));
        when(fees.findByIdForUpdate(25L)).thenReturn(Optional.of(fee));
        when(allocations.findByFeeIdOrderByAllocatedAtAsc(25L))
                .thenReturn(List.of(allocation(new BigDecimal("10000.00"))));
        when(refunds.findByFeeIdAndStatus(25L, com.sis.entity.RefundStatus.COMPLETED))
                .thenReturn(List.of());
        when(waivers.findByFeeIdAndActiveTrueOrderByApprovedAtDesc(25L)).thenReturn(List.of());

        PaymentAttemptService service = new PaymentAttemptService(
                attempts, fees, allocations, waivers, auditLogs, mock(UserRepository.class), transactions,
                mock(PaymentDestinationService.class), mock(NotificationService.class), refunds,
                mock(NotificationRecipientService.class));

        assertThatThrownBy(() -> service.confirmLateFlutterwaveReconciliation(96L, "finance", "123458"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Late Flutterwave payment exceeds the outstanding fee balance; finance review is required");
        verify(attempts, never()).save(any(PaymentAttempt.class));
        verify(allocations, never()).save(any(PaymentAllocation.class));
        verify(transactions, never()).save(any(PaymentTransaction.class));
    }

    @Test
    void flutterwaveCheckoutUsesStudentContactEmailInsteadOfAccountEmail() {
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
        user.setEmail("account@example.test");
        Student student = new Student(user, "Student", "Grade 10");
        student.setId(14L);
        student.setEmail("contact@example.test");
        Fee fee = new Fee();
        fee.setId(25L);
        fee.setStudent(student);
        fee.setAmount(new BigDecimal("100.00"));
        when(fees.findByIdForUpdate(25L)).thenReturn(Optional.of(fee));
        when(destinations.list()).thenReturn(List.of(new PaymentDestinationView(
                PaymentChannel.FLUTTERWAVE, "Flutterwave", true, null, null)));
        when(attempts.findFirstByFeeIdAndStudentIdAndPaymentChannelAndStatusOrderByCreatedAtDesc(
                25L, 14L, PaymentChannel.FLUTTERWAVE, PaymentAttemptStatus.PENDING))
                .thenReturn(Optional.empty());
        when(allocations.findByFeeIdOrderByAllocatedAtAsc(25L)).thenReturn(List.of());
        when(attempts.sumAmountByFeeIdAndStatus(25L, PaymentAttemptStatus.PENDING))
                .thenReturn(BigDecimal.ZERO);
        when(waivers.findByFeeIdAndActiveTrueOrderByApprovedAtDesc(25L)).thenReturn(List.of());
        when(attempts.save(any(PaymentAttempt.class))).thenAnswer(invocation -> {
            PaymentAttempt attempt = invocation.getArgument(0);
            attempt.setId(91L);
            return attempt;
        });
        PaymentAttemptService service = new PaymentAttemptService(
                attempts, fees, allocations, waivers, auditLogs, users, transactions,
                destinations, notifications, refunds, mock(NotificationRecipientService.class));

        FlutterwaveCheckoutDetails details = service.createFlutterwaveAttempt(25L, 14L);

        assertThat(details.getEmail()).isEqualTo("contact@example.test");
    }

    @Test
    void flutterwaveCheckoutRejectsMissingContactEmailEvenWhenAccountEmailExists() {
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
        user.setEmail("account@example.test");
        Student student = new Student(user, "Student", "Grade 10");
        student.setId(14L);
        Fee fee = new Fee();
        fee.setId(25L);
        fee.setStudent(student);
        fee.setAmount(new BigDecimal("100.00"));
        when(fees.findByIdForUpdate(25L)).thenReturn(Optional.of(fee));
        when(destinations.list()).thenReturn(List.of(new PaymentDestinationView(
                PaymentChannel.FLUTTERWAVE, "Flutterwave", true, null, null)));
        PaymentAttemptService service = new PaymentAttemptService(
                attempts, fees, allocations, waivers, auditLogs, users, transactions,
                destinations, notifications, refunds, mock(NotificationRecipientService.class));

        assertThatThrownBy(() -> service.createFlutterwaveAttempt(25L, 14L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Add a valid email address to your student profile before paying online");
        verify(attempts, never()).save(any(PaymentAttempt.class));
    }

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
                attempts, fees, allocations, waivers, auditLogs, users, transactions, destinations, notifications, refunds,
                mock(NotificationRecipientService.class));

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
                attempts, fees, allocations, waivers, auditLogs, users, transactions, destinations, notifications, refunds,
                mock(NotificationRecipientService.class));
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
                attempts, fees, allocations, waivers, auditLogs, users, transactions, destinations, notifications, refunds,
                mock(NotificationRecipientService.class));

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
                attempts, fees, allocations, waivers, auditLogs, users, transactions, destinations, notifications, refunds,
                mock(NotificationRecipientService.class));
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
                attempts, fees, allocations, waivers, auditLogs, users, transactions, destinations, notifications, refunds,
                mock(NotificationRecipientService.class));
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
