package com.sis.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class FlutterwaveCheckoutDetails {
    private final Long attemptId;
    private final Long feeId;
    private final String txRef;
    private final BigDecimal amount;
    private final String email;
    private final String customerName;
    private final String phoneNumber;
    private final String checkoutUrl;
    private final LocalDateTime expiresAt;
    private final boolean existingCheckout;

    public FlutterwaveCheckoutDetails(Long attemptId, Long feeId, String txRef, BigDecimal amount,
                                      String email, String customerName, String phoneNumber,
                                      String checkoutUrl, LocalDateTime expiresAt, boolean existingCheckout) {
        this.attemptId = attemptId;
        this.feeId = feeId;
        this.txRef = txRef;
        this.amount = amount;
        this.email = email;
        this.customerName = customerName;
        this.phoneNumber = phoneNumber;
        this.checkoutUrl = checkoutUrl;
        this.expiresAt = expiresAt;
        this.existingCheckout = existingCheckout;
    }

    public Long getAttemptId() { return attemptId; }
    public Long getFeeId() { return feeId; }
    public String getTxRef() { return txRef; }
    public BigDecimal getAmount() { return amount; }
    public String getEmail() { return email; }
    public String getCustomerName() { return customerName; }
    public String getPhoneNumber() { return phoneNumber; }
    public String getCheckoutUrl() { return checkoutUrl; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public boolean isExistingCheckout() { return existingCheckout; }
}
