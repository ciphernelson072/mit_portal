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
    void shouldStartWithResendApiMailWithoutSmtpSettings() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "jwt.secret=0123456789abcdef0123456789abcdef",
                        "spring.datasource.url=jdbc:h2:mem:prod-resend-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                        "spring.datasource.username=test-user",
                        "spring.datasource.password=test-password",
                        "app.frontend.url=https://portal.example",
                        "cors.allowed-origins=https://portal.example",
                        "file.upload.directory=./target/test-uploads",
                        "app.mail.enabled=true",
                        "app.mail.provider=resend",
                        "app.mail.api-key=re_test_api_key_value",
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
    void shouldRejectPlaceholderResendApiKey() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "jwt.secret=0123456789abcdef0123456789abcdef",
                        "spring.datasource.url=jdbc:h2:mem:prod-resend-placeholder-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                        "spring.datasource.username=test-user",
                        "spring.datasource.password=test-password",
                        "app.frontend.url=https://portal.example",
                        "cors.allowed-origins=https://portal.example",
                        "file.upload.directory=./target/test-uploads",
                        "app.mail.enabled=true",
                        "app.mail.provider=resend",
                        "app.mail.api-key=REPLACE_WITH_NEW_RESEND_API_KEY",
                        "app.mail.from-email=portal@verified.example",
                        "app.finance.webhook.secret=0123456789abcdef0123456789abcdef",
                        "app.security.cookie-secure=true",
                        "app.security.cookie-same-site=Strict"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("MAIL_API_KEY must not use a placeholder value");
                });
    }
}
