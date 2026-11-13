package com.sis.entity;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "fee_waivers")
public class FeeWaiver {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "fee_id", nullable = false) private Long feeId;
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal amount;
    @Column(nullable = false, length = 1000) private String reason;
    @Column(name = "approved_by", nullable = false, length = 255) private String approvedBy;
    @Column(name = "approved_at", nullable = false) private LocalDateTime approvedAt = LocalDateTime.now();
    @Column(nullable = false) private Boolean active = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getFeeId() { return feeId; }
    public void setFeeId(Long feeId) { this.feeId = feeId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
}