package com.sis.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "assets", uniqueConstraints = @UniqueConstraint(name = "uk_asset_tag", columnNames = "asset_tag"))
public class Asset {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "asset_tag", nullable = false, length = 50) private String assetTag;
    @Column(nullable = false, length = 200) private String name;
    @Column(length = 1000) private String description;
    @Column(precision = 12, scale = 2) private BigDecimal purchaseValue;
    @Column(name = "purchase_date") private LocalDate purchaseDate;
    @Column(name = "assigned_to_user_id") private Long assignedToUserId;
    @Column(nullable = false) private Boolean active = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAssetTag() { return assetTag; }
    public void setAssetTag(String assetTag) { this.assetTag = assetTag; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getPurchaseValue() { return purchaseValue; }
    public void setPurchaseValue(BigDecimal purchaseValue) { this.purchaseValue = purchaseValue; }
    public LocalDate getPurchaseDate() { return purchaseDate; }
    public void setPurchaseDate(LocalDate purchaseDate) { this.purchaseDate = purchaseDate; }
    public Long getAssignedToUserId() { return assignedToUserId; }
    public void setAssignedToUserId(Long assignedToUserId) { this.assignedToUserId = assignedToUserId; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
}