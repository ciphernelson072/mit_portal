package com.sis.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.io.IOException;

/** Fails production startup when deployment-critical settings are missing or unsafe. */
@Component
@Profile("prod")
public class ProductionConfigurationValidator {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${spring.datasource.url}")
    private String databaseUrl;

    @Value("${spring.datasource.username}")
    private String databaseUsername;

    @Value("${spring.datasource.password}")
    private String databasePassword;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Value("${cors.allowed-origins}")
    private String allowedOrigins;

    @Value("${file.upload.directory}")
    private String uploadDirectory;

    @Value("${app.mail.enabled:true}")
    private boolean mailEnabled;

    @Value("${spring.mail.host:}")
    private String mailHost;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    @Value("${app.mail.provider:smtp}")
    private String mailProvider;

    @Value("${app.mail.api-key:}")
    private String mailApiKey;

    @Value("${app.mail.from-email:}")
    private String mailApiFromEmail;

    @Value("${app.finance.webhook.secret:}")
    private String paymentWebhookSecret;

    @Value("${app.security.cookie-secure:true}")
    private boolean cookieSecure;

    @Value("${app.security.cookie-same-site:Strict}")
    private String cookieSameSite;

    @Value("${sis.sample-data.enabled:false}")
    private boolean sampleDataEnabled;

    @Value("${spring.flyway.baseline-on-migrate:false}")
    private boolean flywayBaselineOnMigrate;

    @PostConstruct
    void validate() {
        requireAtLeast("JWT_SECRET", jwtSecret, 32);
        requireConfigured("DB_URL", databaseUrl);
        requireConfigured("DB_USER", databaseUsername);
        requireConfigured("DB_PASSWORD", databasePassword);
        requireConfigured("FRONTEND_URL", frontendUrl);
        requireConfigured("CORS_ALLOWED_ORIGINS", allowedOrigins);

        validateFrontendUrl(frontendUrl);
        validateAllowedOrigins(allowedOrigins, frontendUrl);

        if (databaseUsername.equalsIgnoreCase("root")) {
            throw new IllegalStateException("DB_USER must not be root in production.");
        }
        if (sampleDataEnabled) {
            throw new IllegalStateException("SAMPLE_DATA_ENABLED must be false in production.");
        }
        if (flywayBaselineOnMigrate) {
            throw new IllegalStateException(
                    "FLYWAY_BASELINE_ON_MIGRATE must be false in production; review and baseline existing databases explicitly.");
        }
        if (uploadDirectory == null || uploadDirectory.isBlank()) {
            throw new IllegalStateException("UPLOAD_DIR must be configured in production.");
        }
        if (mailEnabled) {
            if ("resend".equalsIgnoreCase(mailProvider)) {
                requireConfigured("MAIL_API_KEY", mailApiKey);
                rejectPlaceholder("MAIL_API_KEY", mailApiKey);
                requireConfigured("MAIL_FROM_EMAIL", mailApiFromEmail);
                rejectPlaceholder("MAIL_FROM_EMAIL", mailApiFromEmail);
            } else if ("smtp".equalsIgnoreCase(mailProvider)) {
                requireConfigured("MAIL_HOST", mailHost);
                requireConfigured("MAIL_USERNAME", mailUsername);
                requireConfigured("MAIL_PASSWORD", mailPassword);
            } else {
                throw new IllegalStateException("MAIL_PROVIDER must be either smtp or resend.");
            }
        }
        requireAtLeast("PAYMENT_WEBHOOK_SECRET", paymentWebhookSecret, 32);
        if (!"Strict".equalsIgnoreCase(cookieSameSite)) {
            throw new IllegalStateException(
                    "COOKIE_SAME_SITE must remain Strict while CSRF protection uses Origin validation.");
        }
        if (!cookieSecure) {
            throw new IllegalStateException("COOKIE_SECURE must be true in production.");
        }
        try {
            Path uploadPath = Path.of(uploadDirectory);
            if (uploadPath.toString().equals(".")) {
                throw new IllegalStateException("UPLOAD_DIR cannot point to the current working directory in production.");
            }
            Files.createDirectories(uploadPath);
            if (!Files.isWritable(uploadPath)) {
                throw new IllegalStateException("UPLOAD_DIR is not writable: " + uploadDirectory);
            }
        } catch (IOException | InvalidPathException | SecurityException ex) {
            throw new IllegalStateException("UPLOAD_DIR cannot be prepared: " + uploadDirectory, ex);
        }
    }

    private void validateFrontendUrl(String frontendUrl) {
        URI frontendUri = parseUri(frontendUrl, "FRONTEND_URL");
        if (!"https".equalsIgnoreCase(frontendUri.getScheme()) || frontendUri.getHost() == null
                || frontendUri.getRawUserInfo() != null || frontendUri.getRawQuery() != null
                || frontendUri.getRawFragment() != null) {
            throw new IllegalStateException("FRONTEND_URL must be a valid HTTPS URL without credentials, query, or fragment.");
        }
    }

    private void validateAllowedOrigins(String rawOrigins, String frontendUrl) {
        if (rawOrigins == null || rawOrigins.isBlank()) {
            throw new IllegalStateException("CORS_ALLOWED_ORIGINS must not be blank in production.");
        }

        URI frontendUri = parseUri(frontendUrl, "FRONTEND_URL");
        String frontendOrigin = originOf(frontendUri);
        boolean containsFrontendOrigin = false;
        String[] origins = rawOrigins.split(",", -1);
        for (String origin : origins) {
            String cleaned = origin.trim();
            if (cleaned.isEmpty()) {
                throw new IllegalStateException("CORS_ALLOWED_ORIGINS contains an empty entry.");
            }
            if (cleaned.contains("*")) {
                throw new IllegalStateException("CORS_ALLOWED_ORIGINS must not contain a wildcard origin in production.");
            }
            URI originUri = parseUri(cleaned, "CORS_ALLOWED_ORIGINS");
            if (!"https".equalsIgnoreCase(originUri.getScheme()) || originUri.getHost() == null
                    || originUri.getRawUserInfo() != null || originUri.getRawQuery() != null
                    || originUri.getRawFragment() != null || (originUri.getRawPath() != null && !originUri.getRawPath().isEmpty())) {
                throw new IllegalStateException("CORS_ALLOWED_ORIGINS entries must be HTTPS origins without paths or credentials.");
            }
            if (frontendOrigin.equals(originOf(originUri))) {
                containsFrontendOrigin = true;
            }
        }
        if (!containsFrontendOrigin) {
            throw new IllegalStateException("CORS_ALLOWED_ORIGINS must include the FRONTEND_URL origin.");
        }
    }

    private URI parseUri(String value, String propertyName) {
        try {
            return new URI(value);
        } catch (URISyntaxException ex) {
            throw new IllegalStateException(propertyName + " must be a valid URL.", ex);
        }
    }

    private String originOf(URI uri) {
        return uri.getScheme().toLowerCase(java.util.Locale.ROOT) + "://"
                + uri.getRawAuthority().toLowerCase(java.util.Locale.ROOT);
    }

    private void requireConfigured(String name, String value) {
        if (value == null || value.isBlank() || value.startsWith("${")) {
            throw new IllegalStateException(name + " must be configured in production.");
        }
    }

    private void rejectPlaceholder(String name, String value) {
        String normalized = value.toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("replace-with") || normalized.contains("replace_with")
                || normalized.startsWith("<")) {
            throw new IllegalStateException(name + " must not use a placeholder value in production.");
        }
    }

    private void requireAtLeast(String name, String value, int minimumLength) {
        requireConfigured(name, value);
        if (value.toLowerCase(java.util.Locale.ROOT).contains("replace-with")) {
            throw new IllegalStateException(name + " must not use a placeholder value in production.");
        }
        if (value.length() < minimumLength) {
            throw new IllegalStateException(name + " must contain at least " + minimumLength + " characters.");
        }
    }
}
