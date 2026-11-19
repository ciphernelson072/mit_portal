package com.sis.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class FlutterwaveVerifyRequest {
    @NotBlank
    @Size(max = 120)
    private String txRef;

    @NotNull
    @Positive
    private Long transactionId;

    public String getTxRef() { return txRef; }
    public void setTxRef(String txRef) { this.txRef = txRef; }
    public Long getTransactionId() { return transactionId; }
    public void setTransactionId(Long transactionId) { this.transactionId = transactionId; }
}
