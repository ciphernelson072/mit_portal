package com.sis.service;

import com.sis.dto.CreateReconciliationEntryRequest;
import com.sis.entity.AuditLog;
import com.sis.entity.BankReconciliationEntry;
import com.sis.entity.PaymentAttempt;
import com.sis.entity.PaymentAttemptStatus;
import com.sis.entity.PaymentChannel;
import com.sis.entity.ReconciliationStatus;
import com.sis.entity.User;
import com.sis.repository.AuditLogRepository;
import com.sis.repository.BankReconciliationEntryRepository;
import com.sis.repository.PaymentAttemptRepository;
import com.sis.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReconciliationServiceTest {

    @Test
    void trimsProviderReferenceBeforeMatchingAndImport() {
        BankReconciliationEntryRepository entries = mock(BankReconciliationEntryRepository.class);
        PaymentAttemptRepository attempts = mock(PaymentAttemptRepository.class);
        PaymentAttemptService paymentAttempts = mock(PaymentAttemptService.class);
        AuditLogRepository auditLogs = mock(AuditLogRepository.class);
        UserRepository users = mock(UserRepository.class);
        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setId(7L);
        attempt.setPaymentChannel(PaymentChannel.BANK_TRANSFER);
        attempt.setAmount(new BigDecimal("50.00"));
        attempt.setStatus(PaymentAttemptStatus.PENDING);
        when(attempts.findByProviderReference("BANK-REF")).thenReturn(Optional.of(attempt));
        when(entries.save(any(BankReconciliationEntry.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        User user = new User();
        user.setId(12L);
        when(users.findByUsernameNormalized("finance")).thenReturn(Optional.of(user));

        ReconciliationService service = new ReconciliationService(
                entries, attempts, paymentAttempts, auditLogs, users);
        CreateReconciliationEntryRequest request = new CreateReconciliationEntryRequest();
        request.setProviderReference("  BANK-REF  ");
        request.setStatementAmount(new BigDecimal("50.00"));
        request.setStatementDate(LocalDate.of(2026, 10, 30));

        BankReconciliationEntry imported = service.importEntry(request, "finance");

        assertThat(imported.getProviderReference()).isEqualTo("BANK-REF");
        assertThat(imported.getMatchedPaymentAttemptId()).isEqualTo(7L);
        assertThat(imported.getStatus()).isEqualTo(ReconciliationStatus.MATCHED);
        verify(paymentAttempts).confirm(7L, "finance");
        verify(auditLogs).save(any(AuditLog.class));
    }
}
