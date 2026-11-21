package com.sis.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import com.sis.exception.HumanVerificationUnavailableException;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.Map;

@Service
public class TurnstileVerificationService {
    private static final String SITEVERIFY_URL =
            "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    private final boolean enabled;
    private final String siteKey;
    private final String secretKey;
    private final RestTemplate restTemplate;

    @Autowired
    public TurnstileVerificationService(
            @Value("${app.security.turnstile.enabled:false}") boolean enabled,
            @Value("${app.security.turnstile.site-key:}") String siteKey,
            @Value("${app.security.turnstile.secret-key:}") String secretKey) {
        this(enabled, siteKey, secretKey, createRestTemplate());
    }

    TurnstileVerificationService(
            boolean enabled, String siteKey, String secretKey, RestTemplate restTemplate) {
        this.enabled = enabled;
        this.siteKey = siteKey;
        this.secretKey = secretKey;
        this.restTemplate = restTemplate;
    }

    private static RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        return new RestTemplate(factory);
    }

    @PostConstruct
    void validateConfiguration() {
        if (enabled && (!StringUtils.hasText(siteKey) || !StringUtils.hasText(secretKey))) {
            throw new IllegalStateException(
                    "TURNSTILE_ENABLED requires both TURNSTILE_SITE_KEY and TURNSTILE_SECRET_KEY.");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getSiteKey() {
        return enabled ? siteKey : "";
    }

    public void verify(String token, String expectedAction) {
        if (!enabled) {
            return;
        }
        if (!StringUtils.hasText(token) || token.length() > 2048 || !StringUtils.hasText(expectedAction)) {
            throw new IllegalArgumentException("Complete the human verification challenge and try again.");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        SiteverifyResponse result;
        try {
            result = restTemplate.postForObject(
                    SITEVERIFY_URL,
                    new HttpEntity<>(Map.of("secret", secretKey, "response", token.trim()), headers),
                    SiteverifyResponse.class);
        } catch (RestClientException exception) {
            throw new HumanVerificationUnavailableException(
                    "Human verification is temporarily unavailable. Please try again.", exception);
        }
        if (result == null || !result.success() || !expectedAction.equals(result.action())) {
            throw new IllegalArgumentException("Human verification failed. Please try again.");
        }
    }

    public record SiteverifyResponse(boolean success, String action) {
    }
}
