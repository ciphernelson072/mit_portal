package com.sis.dto;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public class CreateReconciliationEntryRequest {
    @NotBlank private String providerReference;
    @NotNull @DecimalMin("0.01") private BigDecimal statementAmount;
    @NotNull private LocalDate statementDate;

    public String getProviderReference() { return providerReference; }
    public void setProviderReference(String providerReference) { this.providerReference = providerReference; }
    public BigDecimal getStatementAmount() { return statementAmount; }
    public void setStatementAmount(BigDecimal statementAmount) { this.statementAmount = statementAmount; }
    public LocalDate getStatementDate() { return statementDate; }
    public void setStatementDate(LocalDate statementDate) { this.statementDate = statementDate; }
}