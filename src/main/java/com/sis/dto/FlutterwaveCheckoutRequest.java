package com.sis.dto;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;

public class FlutterwaveCheckoutRequest {
    @NotNull
    @Positive
    private Long feeId;

    public Long getFeeId() { return feeId; }
    public void setFeeId(Long feeId) { this.feeId = feeId; }
}
