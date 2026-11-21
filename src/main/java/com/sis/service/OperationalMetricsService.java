package com.sis.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

@Service
public class OperationalMetricsService {
    private final Counter httpBadRequests;
    private final Counter httpServerErrors;
    private final Counter authenticationFailures;
    private final Counter accessDenied;
    private final Counter mfaRecoveryCodeConsumption;
    private final Counter backupHealthAlerts;
    private final Counter rateLimitBlocked;

    public OperationalMetricsService(MeterRegistry registry) {
        httpBadRequests = registry.counter("sis.http.bad_requests");
        httpServerErrors = registry.counter("sis.http.server_errors");
        authenticationFailures = registry.counter("sis.authentication.failures");
        accessDenied = registry.counter("sis.http.access_denied");
        mfaRecoveryCodeConsumption = registry.counter("sis.mfa.recovery_codes.consumed");
        backupHealthAlerts = registry.counter("sis.backup.health_alerts");
        rateLimitBlocked = registry.counter("sis.ratelimit.blocked");
    }

    public void badRequest() { httpBadRequests.increment(); }
    public void serverError() { httpServerErrors.increment(); }
    public void authenticationFailure() { authenticationFailures.increment(); }
    public void accessDenied() { accessDenied.increment(); }
    public void mfaRecoveryCodeConsumed() { mfaRecoveryCodeConsumption.increment(); }
    public void backupHealthAlert(String status) {
        backupHealthAlerts.increment();
    }
    public void rateLimitBlocked(String limiter) {
        rateLimitBlocked.increment();
    }
}
