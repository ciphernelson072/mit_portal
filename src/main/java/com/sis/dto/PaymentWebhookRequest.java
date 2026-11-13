package com.sis.dto;

import javax.validation.constraints.NotBlank;

public class PaymentWebhookRequest {
    @NotBlank private String providerReference;
    @NotBlank private String status;

    public String getProviderReference() { return providerReference; }
    public void setProviderReference(String providerReference) { this.providerReference = providerReference; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}