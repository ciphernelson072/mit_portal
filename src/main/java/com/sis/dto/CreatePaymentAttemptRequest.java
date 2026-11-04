package com.sis.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import com.sis.entity.PaymentChannel;

public class CreatePaymentAttemptRequest {
    @NotNull private Long feeId;
    @NotNull @DecimalMin(value = "0.01") private BigDecimal amount;
    @NotNull private PaymentChannel paymentChannel;
    @NotBlank @Size(max = 120) private String providerReference;

    public Long getFeeId() { return feeId; }
    public void setFeeId(Long feeId) { this.feeId = feeId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public PaymentChannel getPaymentChannel() { return paymentChannel; }
    public void setPaymentChannel(PaymentChannel paymentChannel) { this.paymentChannel = paymentChannel; }
    public String getProviderReference() { return providerReference; }
    public void setProviderReference(String providerReference) { this.providerReference = providerReference; }
}