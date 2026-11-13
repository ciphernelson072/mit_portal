package com.sis.entity;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "bank_reconciliation_entries", uniqueConstraints = @UniqueConstraint(name = "uk_bank_statement_reference", columnNames = "provider_reference"))
public class BankReconciliationEntry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "provider_reference", nullable = false, length = 120) private String providerReference;
    @Column(name = "statement_amount", nullable = false, precision = 10, scale = 2) private BigDecimal statementAmount;
    @Column(name = "statement_date", nullable = false) private LocalDate statementDate;
    @Column(name = "matched_payment_attempt_id") private Long matchedPaymentAttemptId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private ReconciliationStatus status = ReconciliationStatus.UNMATCHED;
    @Column(name = "reconciled_by") private String reconciledBy;
    @Column(name = "reconciled_at") private LocalDateTime reconciledAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getProviderReference() { return providerReference; }
    public void setProviderReference(String providerReference) { this.providerReference = providerReference; }
    public BigDecimal getStatementAmount() { return statementAmount; }
    public void setStatementAmount(BigDecimal statementAmount) { this.statementAmount = statementAmount; }
    public LocalDate getStatementDate() { return statementDate; }
    public void setStatementDate(LocalDate statementDate) { this.statementDate = statementDate; }
    public Long getMatchedPaymentAttemptId() { return matchedPaymentAttemptId; }
    public void setMatchedPaymentAttemptId(Long matchedPaymentAttemptId) { this.matchedPaymentAttemptId = matchedPaymentAttemptId; }
    public ReconciliationStatus getStatus() { return status; }
    public void setStatus(ReconciliationStatus status) { this.status = status; }
    public String getReconciledBy() { return reconciledBy; }
    public void setReconciledBy(String reconciledBy) { this.reconciledBy = reconciledBy; }
    public LocalDateTime getReconciledAt() { return reconciledAt; }
    public void setReconciledAt(LocalDateTime reconciledAt) { this.reconciledAt = reconciledAt; }
}