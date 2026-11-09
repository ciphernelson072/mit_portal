package com.sis.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sis.dto.FlutterwaveCheckoutDetails;
import com.sis.dto.FlutterwaveCheckoutResponse;
import com.sis.entity.PaymentAttempt;
import com.sis.entity.PaymentAttemptStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class FlutterwavePaymentService {
    private static final Logger logger = LoggerFactory.getLogger(FlutterwavePaymentService.class);
    private static final String API_BASE = "https://api.flutterwave.com/v3";
    private static final String CURRENCY = "UGX";
    private static final long CHECKOUT_SESSION_MINUTES = 60;

    private final PaymentAttemptService attempts;
    private final RestTemplate restTemplate;

    @Value("${app.finance.flutterwave.secret-key:}")
    private String secretKey;

    @Value("${app.finance.flutterwave.webhook-secret-hash:}")
    private String webhookSecretHash;

    @Value("${app.frontend.url:http://localhost:8080}")
    private String frontendUrl;

    @Autowired
    public FlutterwavePaymentService(PaymentAttemptService attempts) {
        this(attempts, createRestTemplate());
    }

    FlutterwavePaymentService(PaymentAttemptService attempts, RestTemplate restTemplate) {
        this.attempts = attempts;
        this.restTemplate = restTemplate;
    }

    public FlutterwaveCheckoutResponse createCheckout(Long feeId, Long studentId) {
        ensureConfigured();
        FlutterwaveCheckoutDetails checkout = attempts.createFlutterwaveAttempt(feeId, studentId);
        if (checkout.isExistingCheckout()) {
            return response(checkout, checkout.getCheckoutUrl());
        }

        Map<String, Object> payload = createPayload(checkout);
        try {
            JsonNode body = restTemplate.exchange(
                    API_BASE + "/payments", HttpMethod.POST, request(payload), JsonNode.class).getBody();
            String link = body == null ? null : body.path("data").path("link").asText(null);
            if (body == null || !"success".equalsIgnoreCase(body.path("status").asText())
                    || !isTrustedCheckoutUrl(link)) {
                attempts.failFlutterwaveCheckoutCreation(checkout.getAttemptId());
                throw new IllegalStateException("Flutterwave did not return a valid hosted checkout link");
            }
            attempts.recordFlutterwaveCheckout(checkout.getAttemptId(), link);
            return response(checkout, link);
        } catch (RestClientException exception) {
            attempts.failFlutterwaveCheckoutCreation(checkout.getAttemptId());
            logger.warn("Flutterwave could not create checkout for payment attempt {}", checkout.getAttemptId());
            throw new IllegalStateException("Flutterwave checkout is temporarily unavailable. Please try again later.");
        }
    }

    public PaymentAttempt verifyForStudent(String txRef, Long transactionId, Long studentId) {
        ensureConfigured();
        PaymentAttempt attempt = attempts.findStudentFlutterwaveAttempt(txRef, studentId);
        return verifyAndFinalize(attempt, transactionId, true);
    }

    public void handleWebhook(JsonNode payload) {
        if (!"charge.completed".equals(payload.path("event").asText())) {
            return;
        }
        JsonNode data = payload.path("data");
        if (!data.path("id").canConvertToLong() || data.path("id").asLong() <= 0
                || !data.path("tx_ref").isTextual()) {
            logger.warn("Ignoring Flutterwave webhook with missing transaction identity");
            return;
        }
        String txRef = data.path("tx_ref").asText();
        PaymentAttempt attempt = attempts.findFlutterwaveAttemptIfPresent(txRef).orElse(null);
        if (attempt == null) {
            logger.warn("Ignoring Flutterwave webhook for an unknown payment reference");
            return;
        }
        ensureConfigured();
        verifyAndFinalize(attempt, data.path("id").asLong(), false);
    }

    private PaymentAttempt verifyAndFinalize(PaymentAttempt attempt, Long transactionId, boolean failOnMismatch) {
        if (attempt.getStatus() == PaymentAttemptStatus.COMPLETED) {
            if (Long.toString(transactionId).equals(attempt.getProviderTransactionId())) {
                return attempt;
            }
            throw new IllegalStateException("Payment attempt is already linked to a different transaction");
        }
        if (attempt.getStatus() == PaymentAttemptStatus.FAILED
                && Long.toString(transactionId).equals(attempt.getProviderTransactionId())) {
            return attempt;
        }
        JsonNode body;
        try {
            body = restTemplate.exchange(
                    API_BASE + "/transactions/" + transactionId + "/verify",
                    HttpMethod.GET, request(null), JsonNode.class).getBody();
        } catch (RestClientException exception) {
            logger.warn("Flutterwave transaction verification failed for payment attempt {}", attempt.getId());
            throw new IllegalStateException("Payment verification is temporarily unavailable. Please check again.");
        }

        JsonNode data = body == null ? null : body.path("data");
        boolean matches = body != null
                && "success".equalsIgnoreCase(body.path("status").asText())
                && data != null
                && Long.toString(transactionId).equals(data.path("id").asText())
                && attempt.getProviderReference().equals(data.path("tx_ref").asText())
                && CURRENCY.equalsIgnoreCase(data.path("currency").asText())
                && amountMatches(attempt.getAmount(), data.path("amount"));
        if (!matches) {
            logger.warn("Flutterwave verification details did not match payment attempt {}", attempt.getId());
            if (failOnMismatch) {
                throw new IllegalStateException("The Flutterwave transaction could not be verified for this fee.");
            }
            return attempt;
        }

        String status = data.path("status").asText();
        if ("successful".equalsIgnoreCase(status)) {
            if (attempt.getStatus() == PaymentAttemptStatus.FAILED) {
                logger.warn("Verified Flutterwave payment arrived after attempt {} was closed; finance reconciliation is required",
                        attempt.getId());
                return attempt;
            }
            return attempts.confirmFlutterwave(attempt.getId(), Long.toString(transactionId));
        }
        return attempt;
    }

    private HttpEntity<?> request(Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(secretKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private Map<String, Object> createPayload(FlutterwaveCheckoutDetails checkout) {
        Map<String, Object> customer = new LinkedHashMap<>();
        customer.put("email", checkout.getEmail());
        if (checkout.getCustomerName() != null && !checkout.getCustomerName().isBlank()) {
            customer.put("name", checkout.getCustomerName());
        }
        if (checkout.getPhoneNumber() != null && !checkout.getPhoneNumber().isBlank()) {
            customer.put("phonenumber", checkout.getPhoneNumber());
        }

        Map<String, Object> customizations = new LinkedHashMap<>();
        customizations.put("title", "MIT Student Portal");
        customizations.put("description", "Fee payment #" + checkout.getFeeId());

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("payment_attempt_id", checkout.getAttemptId());
        meta.put("fee_id", checkout.getFeeId());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tx_ref", checkout.getTxRef());
        payload.put("amount", checkout.getAmount().toPlainString());
        payload.put("currency", CURRENCY);
        payload.put("redirect_url", callbackUrl());
        payload.put("customer", customer);
        payload.put("customizations", customizations);
        payload.put("meta", meta);
        payload.put("payment_options", "card, mobilemoneyuganda");
        payload.put("session_duration", CHECKOUT_SESSION_MINUTES);
        return payload;
    }

    private String callbackUrl() {
        URI base = URI.create(frontendUrl.trim());
        if (!("https".equalsIgnoreCase(base.getScheme()) || "http".equalsIgnoreCase(base.getScheme()))
                || base.getHost() == null || base.getUserInfo() != null) {
            throw new IllegalStateException("FRONTEND_URL must be a valid HTTP or HTTPS origin for Flutterwave");
        }
        return UriComponentsBuilder.fromUri(base).replacePath("/finance.html")
                .replaceQuery(null).queryParam("payment", "return").build().toUriString();
    }

    private boolean isTrustedCheckoutUrl(String link) {
        if (link == null || link.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(link);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && "checkout.flutterwave.com".equalsIgnoreCase(uri.getHost())
                    && uri.getUserInfo() == null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean amountMatches(BigDecimal expected, JsonNode actual) {
        if (actual == null || actual.isNull()) {
            return false;
        }
        try {
            return expected.compareTo(new BigDecimal(actual.asText())) == 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private FlutterwaveCheckoutResponse response(FlutterwaveCheckoutDetails checkout, String link) {
        return new FlutterwaveCheckoutResponse(checkout.getAttemptId(), checkout.getTxRef(),
                checkout.getAmount(), CURRENCY, link, checkout.getExpiresAt());
    }

    private void ensureConfigured() {
        if (secretKey == null || secretKey.isBlank()
                || webhookSecretHash == null || webhookSecretHash.isBlank()) {
            throw new IllegalStateException("Flutterwave is not configured");
        }
    }

    static RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(15000);
        RestTemplate restTemplate = new RestTemplate(factory);
        restTemplate.getMessageConverters().add(0, new MappingJackson2HttpMessageConverter());
        return restTemplate;
    }
}
