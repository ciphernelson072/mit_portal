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
    public PaymentTransaction() {}
    public PaymentTransaction(Long feeId, Long studentId, BigDecimal amount) { this.feeId = feeId; this.studentId = studentId; this.amount = amount; }
    public Long getId() { return id; }
    public Long getFeeId() { return feeId; }
    public Long getStudentId() { return studentId; }
    public BigDecimal getAmount() { return amount; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
