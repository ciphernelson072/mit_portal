package com.sis.entity;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "procurement_requests")
public class ProcurementRequest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "requester_id", nullable = false) private Long requesterId;
    @Column(nullable = false, length = 255) private String description;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal estimatedAmount;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "supplier_id") private Supplier supplier;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private ProcurementRequestStatus status = ProcurementRequestStatus.SUBMITTED;
    @Column(name = "reviewed_by") private String reviewedBy;
    @Column(name = "reviewed_at") private LocalDateTime reviewedAt;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRequesterId() { return requesterId; }
    public void setRequesterId(Long requesterId) { this.requesterId = requesterId; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getEstimatedAmount() { return estimatedAmount; }
    public void setEstimatedAmount(BigDecimal estimatedAmount) { this.estimatedAmount = estimatedAmount; }
    public Supplier getSupplier() { return supplier; }
    public void setSupplier(Supplier supplier) { this.supplier = supplier; }
    public ProcurementRequestStatus getStatus() { return status; }
    public void setStatus(ProcurementRequestStatus status) { this.status = status; }
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}