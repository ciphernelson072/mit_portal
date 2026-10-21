package com.sis.controller;

import com.sis.dto.PaymentWebhookRequest;
import com.sis.service.PaymentAttemptService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

@RestController
@RequestMapping("/api/public/finance/payments")
public class PaymentWebhookController {
    private final PaymentAttemptService payments;

    @Value("${app.finance.webhook.secret:}")
    private String webhookSecret;

    public PaymentWebhookController(PaymentAttemptService payments) { this.payments = payments; }

    @PostMapping("/callback")
    public ResponseEntity<Map<String, Object>> callback(
            @RequestHeader(value = "X-Payment-Signature", required = false) String signature,
            @Valid @RequestBody PaymentWebhookRequest request) {
        if (webhookSecret == null || webhookSecret.isBlank() || !validSignature(request, signature)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Invalid payment signature"));
        }
        return ResponseEntity.ok(Map.of("status", payments.handleProviderStatus(
                request.getProviderReference(), request.getStatus()).getStatus()));
    }

    private boolean validSignature(PaymentWebhookRequest request, String provided) {
        if (provided == null) return false;
        try {
            String payload = request.getProviderReference() + "." + request.getStatus();
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            return false;
        }
    }
}