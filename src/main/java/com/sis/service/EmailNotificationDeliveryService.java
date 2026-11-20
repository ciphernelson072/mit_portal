package com.sis.service;

import com.sis.entity.EmailDeliveryStatus;
import com.sis.entity.EmailNotificationDelivery;
import com.sis.entity.Notification;
import com.sis.entity.User;
import com.sis.exception.EmailDeliveryException;
import com.sis.repository.EmailNotificationDeliveryRepository;
import com.sis.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class EmailNotificationDeliveryService {
    private static final Logger LOGGER =
            Logger.getLogger(EmailNotificationDeliveryService.class.getName());
    private static final List<Integer> RETRY_DELAY_MINUTES = List.of(1, 5, 30);
    private static final int MAX_ATTEMPTS = RETRY_DELAY_MINUTES.size() + 1;

    private final EmailNotificationDeliveryRepository deliveries;
    private final UserRepository users;
    private final EmailService emailService;

    public EmailNotificationDeliveryService(
            EmailNotificationDeliveryRepository deliveries,
            UserRepository users,
            EmailService emailService) {
        this.deliveries = deliveries;
        this.users = users;
        this.emailService = emailService;
    }

    @Transactional
    public void enqueue(Notification notification) {
        EmailNotificationDelivery delivery = new EmailNotificationDelivery();
        delivery.setNotification(notification);
        delivery.setUserId(notification.getUserId());
        deliveries.save(delivery);
    }

    @Transactional
    public int processDueDeliveries() {
        List<EmailNotificationDelivery> due = deliveries.findDueForUpdate(
                List.of(EmailDeliveryStatus.PENDING, EmailDeliveryStatus.RETRY),
                LocalDateTime.now(),
                PageRequest.of(0, 20));
        for (EmailNotificationDelivery delivery : due) {
            try {
                User user = users.findById(delivery.getUserId())
                        .orElseThrow(() -> new IllegalArgumentException("Notification recipient no longer exists."));
                Notification notification = delivery.getNotification();
                emailService.sendComposedEmail(user.getEmail(), notification.getTitle(), notification.getMessage());
                delivery.setStatus(EmailDeliveryStatus.SENT);
                delivery.setSentAt(LocalDateTime.now());
                delivery.setLastError(null);
            } catch (EmailDeliveryException | IllegalArgumentException failure) {
                recordFailure(delivery, failure);
            }
        }
        return due.size();
    }

    private void recordFailure(EmailNotificationDelivery delivery, RuntimeException failure) {
        int attempts = delivery.getAttemptCount() + 1;
        delivery.setAttemptCount(attempts);
        delivery.setLastError(truncate(failure.getMessage()));
        if (attempts >= MAX_ATTEMPTS) {
            delivery.setStatus(EmailDeliveryStatus.FAILED);
            LOGGER.log(Level.WARNING,
                    "Notification email delivery exhausted retries for delivery id {0}: {1}",
                    new Object[]{delivery.getId(), delivery.getLastError()});
            return;
        }
        delivery.setStatus(EmailDeliveryStatus.RETRY);
        delivery.setNextAttemptAt(LocalDateTime.now().plusMinutes(RETRY_DELAY_MINUTES.get(attempts - 1)));
        LOGGER.log(Level.WARNING,
                "Notification email delivery attempt {0} failed for delivery id {1}; retry scheduled: {2}",
                new Object[]{attempts, delivery.getId(), delivery.getLastError()});
    }

    private String truncate(String message) {
        if (message == null || message.isBlank()) return "Email delivery failed without a provider message.";
        return message.length() <= 512 ? message : message.substring(0, 512);
    }
}
