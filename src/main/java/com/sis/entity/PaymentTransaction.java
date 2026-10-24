package com.sis.entity;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_transactions")
public class PaymentTransaction {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "fee_id", nullable = false) private Long feeId;
    @Column(name = "student_id", nullable = false) private Long studentId;
    @Column(nullable = false) private BigDecimal amount;
    @Column(nullable = false) private String status = "COMPLETED";
    @Column(nullable = false) private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "payment_attempt_id", unique = true) private Long paymentAttemptId;
    @Column(name = "payment_channel", length = 30) private String paymentChannel;
    @Column(name = "provider_reference", length = 120) private String providerReference;
    @Column(name = "provider_transaction_id", length = 80) private String providerTransactionId;
    public PaymentTransaction() {}
    public PaymentTransaction(Long feeId, Long studentId, BigDecimal amount) { this.feeId = feeId; this.studentId = studentId; this.amount = amount; }
    public PaymentTransaction(Long feeId, Long studentId, BigDecimal amount, Long paymentAttemptId,
                              String paymentChannel, String providerReference, String providerTransactionId) {
        this.feeId = feeId;
        this.studentId = studentId;
        this.amount = amount;
        this.paymentAttemptId = paymentAttemptId;
        this.paymentChannel = paymentChannel;
        this.providerReference = providerReference;
        this.providerTransactionId = providerTransactionId;
    }
    public Long getId() { return id; }
    public Long getFeeId() { return feeId; }
    public Long getStudentId() { return studentId; }
    public BigDecimal getAmount() { return amount; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public Long getPaymentAttemptId() { return paymentAttemptId; }
    public String getPaymentChannel() { return paymentChannel; }
    public String getProviderReference() { return providerReference; }
    public String getProviderTransactionId() { return providerTransactionId; }
}
