package com.sis.service;

import com.sis.dto.CreateReconciliationEntryRequest;
import com.sis.entity.AuditLog;
import com.sis.entity.*;
import com.sis.repository.AuditLogRepository;
import com.sis.repository.BankReconciliationEntryRepository;
import com.sis.repository.PaymentAttemptRepository;
import com.sis.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class ReconciliationService {
    private final BankReconciliationEntryRepository entries;
    private final PaymentAttemptRepository attempts;
    private final PaymentAttemptService paymentAttempts;
    private final AuditLogRepository auditLogs;
    private final UserRepository users;

    public ReconciliationService(BankReconciliationEntryRepository entries, PaymentAttemptRepository attempts,
                                 PaymentAttemptService paymentAttempts, AuditLogRepository auditLogs,
                                 UserRepository users) {
        this.entries = entries;
        this.attempts = attempts;
        this.paymentAttempts = paymentAttempts;
        this.auditLogs = auditLogs;
        this.users = users;
    }

    public BankReconciliationEntry importEntry(CreateReconciliationEntryRequest request, String reconciledBy) {
        String providerReference = request.getProviderReference().trim();
        if (entries.findByProviderReference(providerReference).isPresent()) {
            throw new IllegalStateException("Bank statement reference already imported");
        }
        BankReconciliationEntry entry = new BankReconciliationEntry();
        entry.setProviderReference(providerReference);
        entry.setStatementAmount(request.getStatementAmount());
        entry.setStatementDate(request.getStatementDate());
        attempts.findByProviderReference(providerReference).ifPresent(attempt -> {
            entry.setMatchedPaymentAttemptId(attempt.getId());
            if (attempt.getPaymentChannel() == PaymentChannel.BANK_TRANSFER
                    && attempt.getAmount().compareTo(request.getStatementAmount()) == 0
                    && attempt.getStatus() != PaymentAttemptStatus.FAILED) {
                if (attempt.getStatus() == PaymentAttemptStatus.PENDING) {
                    paymentAttempts.confirm(attempt.getId(), reconciledBy);
                }
                entry.setStatus(ReconciliationStatus.MATCHED);
                entry.setReconciledBy(reconciledBy);
                entry.setReconciledAt(LocalDateTime.now());
            } else {
                entry.setStatus(ReconciliationStatus.DISCREPANCY);
            }
        });
        BankReconciliationEntry saved = entries.save(entry);
        Long userId = users.findByUsernameNormalized(reconciledBy)
            .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + reconciledBy))
            .getId();
        auditLogs.save(new AuditLog(userId, "BANK_RECONCILIATION_IMPORTED",
            "Entry #" + saved.getId() + " reference " + saved.getProviderReference() + " amount "
                + saved.getStatementAmount() + " status " + saved.getStatus()));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<BankReconciliationEntry> list(ReconciliationStatus status) {
        return status == null ? entries.findAll() : entries.findByStatusOrderByStatementDateAsc(status);
    }

    @Transactional(readOnly = true)
    public Map<ReconciliationStatus, Long> summary() {
        Map<ReconciliationStatus, Long> result = new EnumMap<>(ReconciliationStatus.class);
        for (ReconciliationStatus status : ReconciliationStatus.values()) {
            result.put(status, entries.findByStatusOrderByStatementDateAsc(status).stream().count());
        }
        return result;
    }
}