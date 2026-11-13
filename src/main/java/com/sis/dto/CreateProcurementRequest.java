package com.sis.dto;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

public class CreateProcurementRequest {
    @NotBlank private String description;
    @NotNull @DecimalMin("0.01") private BigDecimal estimatedAmount;
    private Long supplierId;
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getEstimatedAmount() { return estimatedAmount; }
    public void setEstimatedAmount(BigDecimal estimatedAmount) { this.estimatedAmount = estimatedAmount; }
    public Long getSupplierId() { return supplierId; }
    public void setSupplierId(Long supplierId) { this.supplierId = supplierId; }
}