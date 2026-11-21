package com.sis.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionConfigurationValidatorTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    DataSourceAutoConfiguration.class,
                    HibernateJpaAutoConfiguration.class
            ))
            .withUserConfiguration(ProductionConfigurationValidator.class);

    @Test
    void shouldStartProductionProfileWhenMailIsDisabledAndMailSettingsAreBlank() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "jwt.secret=0123456789abcdef0123456789abcdef",
                        "spring.datasource.url=jdbc:h2:mem:prod-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                        "spring.datasource.username=test-user",
                        "spring.datasource.password=test-password",
                        "app.frontend.url=https://portal.example",
                        "cors.allowed-origins=https://portal.example",
                        "file.upload.directory=./target/test-uploads",
                        "app.mail.enabled=false",
                        "spring.mail.host=",
                        "spring.mail.username=",
                        "spring.mail.password=",
                        "app.finance.webhook.secret=0123456789abcdef0123456789abcdef",
                        "app.security.cookie-secure=true",
                        "app.security.cookie-same-site=Strict"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ProductionConfigurationValidator.class);
                });
    }

    @Test
    void shouldRejectPlaceholderWebhookSecret() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "jwt.secret=0123456789abcdef0123456789abcdef",
                        "spring.datasource.url=jdbc:h2:mem:prod-placeholder-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                        "spring.datasource.username=test-user",
                        "spring.datasource.password=test-password",
                        "app.frontend.url=https://portal.example",
                        "cors.allowed-origins=https://portal.example",
                        "file.upload.directory=./target/test-uploads",
                        "app.mail.enabled=false",
                        "app.finance.webhook.secret=replace-with-at-least-32-random-characters",
                        "app.security.cookie-secure=false",
                        "app.security.cookie-same-site=Strict"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("PAYMENT_WEBHOOK_SECRET must not use a placeholder value");
                });
    }

    @Test
    void shouldRejectCrossSiteCookiePolicyWhileCsrfProtectionIsDisabled() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "jwt.secret=0123456789abcdef0123456789abcdef",
                        "spring.datasource.url=jdbc:h2:mem:prod-samesite-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                        "spring.datasource.username=test-user",
                        "spring.datasource.password=test-password",
                        "app.frontend.url=https://portal.example",
                        "cors.allowed-origins=https://portal.example",
                        "file.upload.directory=./target/test-uploads",
                        "app.mail.enabled=false",
                        "app.finance.webhook.secret=0123456789abcdef0123456789abcdef",
                        "app.security.cookie-secure=true",
                        "app.security.cookie-same-site=None"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("COOKIE_SAME_SITE must remain Strict");
                });
    }

    @Test
    void shouldRejectHttpCorsOriginInProduction() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "jwt.secret=0123456789abcdef0123456789abcdef",
                        "spring.datasource.url=jdbc:h2:mem:prod-http-cors-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                        "spring.datasource.username=test-user",
                        "spring.datasource.password=test-password",
                        "app.frontend.url=https://portal.example",
                        "cors.allowed-origins=https://portal.example,http://legacy.example",
                        "file.upload.directory=./target/test-uploads",
                        "app.mail.enabled=false",
                        "app.finance.webhook.secret=0123456789abcdef0123456789abcdef",
                        "app.security.cookie-secure=true",
                        "app.security.cookie-same-site=Strict"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("CORS_ALLOWED_ORIGINS entries must be HTTPS origins");
                });
    }

    @Test
    void shouldRejectInsecureCookiesInProduction() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "jwt.secret=0123456789abcdef0123456789abcdef",
                        "spring.datasource.url=jdbc:h2:mem:prod-insecure-cookie-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                        "spring.datasource.username=test-user",
                        "spring.datasource.password=test-password",
                        "app.frontend.url=https://portal.example",
                        "cors.allowed-origins=https://portal.example",
                        "file.upload.directory=./target/test-uploads",
                        "app.mail.enabled=false",
                        "app.finance.webhook.secret=0123456789abcdef0123456789abcdef",
                        "app.security.cookie-secure=false",
                        "app.security.cookie-same-site=Strict"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("COOKIE_SECURE must be true in production");
                });
    }

    @Test
    void shouldRejectSampleDataInProduction() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "jwt.secret=0123456789abcdef0123456789abcdef",
                        "spring.datasource.url=jdbc:h2:mem:prod-sample-data-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                        "spring.datasource.username=test-user",
                        "spring.datasource.password=test-password",
                        "app.frontend.url=https://portal.example",
                        "cors.allowed-origins=https://portal.example",
                        "file.upload.directory=./target/test-uploads",
                        "app.mail.enabled=false",
                        "app.finance.webhook.secret=0123456789abcdef0123456789abcdef",
                        "app.security.cookie-secure=true",
                        "app.security.cookie-same-site=Strict",
                        "sis.sample-data.enabled=true"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("SAMPLE_DATA_ENABLED must be false in production");
                });
    }

    @Test
    void shouldRejectAutomaticFlywayBaselineInProduction() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "jwt.secret=0123456789abcdef0123456789abcdef",
                        "spring.datasource.url=jdbc:h2:mem:prod-baseline-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                        "spring.datasource.username=test-user",
                        "spring.datasource.password=test-password",
                        "app.frontend.url=https://portal.example",
                        "cors.allowed-origins=https://portal.example",
                        "file.upload.directory=./target/test-uploads",
                        "app.mail.enabled=false",
                        "app.finance.webhook.secret=0123456789abcdef0123456789abcdef",
                        "app.security.cookie-secure=true",
                        "app.security.cookie-same-site=Strict",
                        "spring.flyway.baseline-on-migrate=true"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("FLYWAY_BASELINE_ON_MIGRATE must be false in production");
                });
    }

    @Test
    void shouldStartWithBrevoApiMailWithoutSmtpSettings() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "jwt.secret=0123456789abcdef0123456789abcdef",
                        "spring.datasource.url=jdbc:h2:mem:prod-brevo-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                        "spring.datasource.username=test-user",
                        "spring.datasource.password=test-password",
                        "app.frontend.url=https://portal.example",
                        "cors.allowed-origins=https://portal.example",
                        "file.upload.directory=./target/test-uploads",
                        "app.mail.enabled=true",
                        "app.mail.provider=brevo",
                        "app.mail.api-key=test-brevo-api-key-value",
                        "app.mail.from-email=portal@verified.example",
                        "app.finance.webhook.secret=0123456789abcdef0123456789abcdef",
                        "app.security.cookie-secure=true",
                        "app.security.cookie-same-site=Strict"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ProductionConfigurationValidator.class);
                });
    }

    @Test
    void shouldRejectPlaceholderBrevoApiKey() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "jwt.secret=0123456789abcdef0123456789abcdef",
                        "spring.datasource.url=jdbc:h2:mem:prod-brevo-placeholder-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                        "spring.datasource.username=test-user",
                        "spring.datasource.password=test-password",
                        "app.frontend.url=https://portal.example",
                        "cors.allowed-origins=https://portal.example",
                        "file.upload.directory=./target/test-uploads",
                        "app.mail.enabled=true",
                        "app.mail.provider=brevo",
                        "app.mail.api-key=REPLACE_WITH_BREVO_API_KEY",
                        "app.mail.from-email=portal@verified.example",
                        "app.finance.webhook.secret=0123456789abcdef0123456789abcdef",
                        "app.security.cookie-secure=true",
                        "app.security.cookie-same-site=Strict"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("BREVO_API_KEY must not use a placeholder value");
                });
    }

    @Test
    void shouldRejectSmtpWhenProductionMailIsEnabled() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "jwt.secret=0123456789abcdef0123456789abcdef",
                        "spring.datasource.url=jdbc:h2:mem:prod-gmail-smtp-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                        "spring.datasource.username=test-user",
                        "spring.datasource.password=test-password",
                        "app.frontend.url=https://portal.example",
                        "cors.allowed-origins=https://portal.example",
                        "file.upload.directory=./target/test-uploads",
                        "app.mail.enabled=true",
                        "app.mail.provider=smtp",
                        "spring.mail.host=smtp.gmail.com",
                        "spring.mail.port=587",
                        "spring.mail.username=sender@gmail.example",
                        "spring.mail.password=test-app-password",
                        "app.mail.from-email=sender@gmail.example",
                        "app.finance.webhook.secret=0123456789abcdef0123456789abcdef",
                        "app.security.cookie-secure=true",
                        "app.security.cookie-same-site=Strict"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("MAIL_PROVIDER must be brevo in production");
                });
    }

    @Test
    void shouldRejectUniversalMfaWithoutEncryptionKey() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "jwt.secret=0123456789abcdef0123456789abcdef",
                        "spring.datasource.url=jdbc:h2:mem:prod-mfa-all-test;MODE=MySQL",
                        "spring.datasource.username=test-user",
                        "spring.datasource.password=test-password",
                        "app.frontend.url=https://portal.example",
                        "cors.allowed-origins=https://portal.example",
                        "file.upload.directory=./target/test-uploads",
                        "app.mail.enabled=false",
                        "app.finance.webhook.secret=0123456789abcdef0123456789abcdef",
                        "app.security.cookie-secure=true",
                        "app.security.cookie-same-site=Strict",
                        "app.security.mfa.enabled=true",
                        "app.security.mfa.required-for-all=true",
                        "app.security.mfa.encryption-key="
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("MFA_ENCRYPTION_KEY");
                });
    }

    @Test
    void shouldStartUniversalMfaWithValidEncryptionKey() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "jwt.secret=0123456789abcdef0123456789abcdef",
                        "spring.datasource.url=jdbc:h2:mem:prod-mfa-all-valid-test;MODE=MySQL",
                        "spring.datasource.username=test-user",
                        "spring.datasource.password=test-password",
                        "app.frontend.url=https://portal.example",
                        "cors.allowed-origins=https://portal.example",
                        "file.upload.directory=./target/test-uploads",
                        "app.mail.enabled=false",
                        "app.finance.webhook.secret=0123456789abcdef0123456789abcdef",
                        "app.security.cookie-secure=true",
                        "app.security.cookie-same-site=Strict",
                        "app.security.mfa.enabled=true",
                        "app.security.mfa.required-for-all=true",
                        "app.security.mfa.encryption-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                });
    }

    @Test
    void shouldRejectUnsupportedProductionReplicaCount() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "jwt.secret=0123456789abcdef0123456789abcdef",
                        "spring.datasource.url=jdbc:h2:mem:prod-replicas-test;MODE=MySQL",
                        "spring.datasource.username=test-user",
                        "spring.datasource.password=test-password",
                        "app.frontend.url=https://portal.example",
                        "cors.allowed-origins=https://portal.example",
                        "file.upload.directory=./target/test-uploads",
                        "app.mail.enabled=false",
                        "app.finance.webhook.secret=0123456789abcdef0123456789abcdef",
                        "app.security.cookie-secure=true",
                        "app.security.cookie-same-site=Strict",
                        "app.deployment.instance-count=2"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("APP_INSTANCE_COUNT must be 1");
                });
    }

    @Test
    void shouldRejectUnsupportedSharedStorageConfiguration() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "jwt.secret=0123456789abcdef0123456789abcdef",
                        "spring.datasource.url=jdbc:h2:mem:prod-storage-test;MODE=MySQL",
                        "spring.datasource.username=test-user",
                        "spring.datasource.password=test-password",
                        "app.frontend.url=https://portal.example",
                        "cors.allowed-origins=https://portal.example",
                        "file.upload.directory=./target/test-uploads",
                        "app.mail.enabled=false",
                        "app.finance.webhook.secret=0123456789abcdef0123456789abcdef",
                        "app.security.cookie-secure=true",
                        "app.security.cookie-same-site=Strict",
                        "app.rate-limit.store=redis"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("RATE_LIMIT_STORE must be memory");
                });
    }

}
