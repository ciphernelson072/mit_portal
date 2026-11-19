package com.sis.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_allocations", uniqueConstraints = @UniqueConstraint(name = "uk_allocation_attempt", columnNames = "payment_attempt_id"))
public class PaymentAllocation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "payment_attempt_id", nullable = false) private Long paymentAttemptId;
    @Column(name = "fee_id", nullable = false) private Long feeId;
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal amount;
    @Column(name = "allocated_at", nullable = false) private LocalDateTime allocatedAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPaymentAttemptId() { return paymentAttemptId; }
    public void setPaymentAttemptId(Long paymentAttemptId) { this.paymentAttemptId = paymentAttemptId; }
    public Long getFeeId() { return feeId; }
    public void setFeeId(Long feeId) { this.feeId = feeId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public LocalDateTime getAllocatedAt() { return allocatedAt; }
    public void setAllocatedAt(LocalDateTime allocatedAt) { this.allocatedAt = allocatedAt; }
}