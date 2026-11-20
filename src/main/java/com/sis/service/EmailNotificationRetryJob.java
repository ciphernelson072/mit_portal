package com.sis.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class EmailNotificationRetryJob {
    private final EmailNotificationDeliveryService deliveries;

    public EmailNotificationRetryJob(EmailNotificationDeliveryService deliveries) {
        this.deliveries = deliveries;
    }

    @Scheduled(fixedDelayString = "${app.automation.email-retry-poll-ms:30000}")
    public void retryQueuedNotificationEmails() {
        deliveries.processDueDeliveries();
    }
}
