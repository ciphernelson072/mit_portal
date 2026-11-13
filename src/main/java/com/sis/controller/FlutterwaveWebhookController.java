package com.sis.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.sis.service.FlutterwavePaymentService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/api/public/finance/payments/flutterwave")
public class FlutterwaveWebhookController {
    private final FlutterwavePaymentService flutterwave;

    @Value("${app.finance.flutterwave.webhook-secret-hash:}")
    private String webhookSecretHash;

    public FlutterwaveWebhookController(FlutterwavePaymentService flutterwave) {
        this.flutterwave = flutterwave;
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(
            @RequestHeader(value = "verif-hash", required = false) String providedHash,
            @RequestBody JsonNode payload) {
        if (!validSecretHash(providedHash)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        flutterwave.handleWebhook(payload);
        return ResponseEntity.ok().build();
    }

    private boolean validSecretHash(String providedHash) {
        if (webhookSecretHash == null || webhookSecretHash.isBlank()
                || providedHash == null || providedHash.isBlank()) {
            return false;
        }
        byte[] expected = webhookSecretHash.getBytes(StandardCharsets.UTF_8);
        byte[] provided = providedHash.getBytes(StandardCharsets.UTF_8);
        return expected.length == provided.length && MessageDigest.isEqual(expected, provided);
    }
}
