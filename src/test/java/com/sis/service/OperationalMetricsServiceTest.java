package com.sis.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OperationalMetricsServiceTest {
    @Test
    void recordsOperationalCountersWithoutSensitivePayloads() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OperationalMetricsService metrics = new OperationalMetricsService(registry);

        metrics.badRequest();
        metrics.serverError();
        metrics.authenticationFailure();
        metrics.accessDenied();
        metrics.mfaRecoveryCodeConsumed();
        metrics.backupHealthAlert("stale");
        metrics.rateLimitBlocked("auth");

        assertThat(registry.get("sis.http.bad_requests").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("sis.http.server_errors").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("sis.authentication.failures").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("sis.http.access_denied").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("sis.mfa.recovery_codes.consumed").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("sis.backup.health_alerts").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("sis.ratelimit.blocked").counter().count()).isEqualTo(1.0);
    }
}
