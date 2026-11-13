package com.sis.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class FlutterwaveCheckoutResponse {
    private final Long attemptId;
    private final String txRef;
    private final BigDecimal amount;
    private final String currency;
    private final String checkoutUrl;
    private final LocalDateTime expiresAt;

    public FlutterwaveCheckoutResponse(Long attemptId, String txRef, BigDecimal amount, String currency,
                                       String checkoutUrl, LocalDateTime expiresAt) {
        this.attemptId = attemptId;
        this.txRef = txRef;
        this.amount = amount;
        this.currency = currency;
        this.checkoutUrl = checkoutUrl;
        this.expiresAt = expiresAt;
    }

    public Long getAttemptId() { return attemptId; }
    public String getTxRef() { return txRef; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getCheckoutUrl() { return checkoutUrl; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
}
