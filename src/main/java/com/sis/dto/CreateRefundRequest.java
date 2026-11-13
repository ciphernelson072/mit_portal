package com.sis.dto;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.math.BigDecimal;

public class CreateRefundRequest {
    @NotNull private Long paymentAttemptId;
    @NotNull @DecimalMin("0.01") private BigDecimal amount;
    @NotBlank @Size(max = 1000) private String reason;

    public Long getPaymentAttemptId() { return paymentAttemptId; }
    public void setPaymentAttemptId(Long paymentAttemptId) { this.paymentAttemptId = paymentAttemptId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}