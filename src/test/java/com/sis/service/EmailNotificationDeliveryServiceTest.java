package com.sis.service;

import com.sis.entity.EmailDeliveryStatus;
import com.sis.entity.EmailNotificationDelivery;
import com.sis.entity.Notification;
import com.sis.entity.User;
import com.sis.exception.EmailDeliveryException;
import com.sis.repository.EmailNotificationDeliveryRepository;
import com.sis.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailNotificationDeliveryServiceTest {
    private final EmailNotificationDeliveryRepository deliveries =
            mock(EmailNotificationDeliveryRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final EmailService emailService = mock(EmailService.class);
    private final EmailNotificationDeliveryService service =
            new EmailNotificationDeliveryService(deliveries, users, emailService);

    @Test
    void sendsDueNotificationAndMarksItSent() {
        EmailNotificationDelivery delivery = delivery(0);
        when(deliveries.findDueForUpdate(anyList(), any(LocalDateTime.class), any()))
                .thenReturn(List.of(delivery));
        when(users.findById(8L)).thenReturn(Optional.of(user()));

        assertThat(service.processDueDeliveries()).isEqualTo(1);

        assertThat(delivery.getStatus()).isEqualTo(EmailDeliveryStatus.SENT);
        assertThat(delivery.getSentAt()).isNotNull();
        verify(emailService).sendComposedEmail("student@example.test", "Attendance update", "Please review attendance.");
    }

    @Test
    void retriesTemporaryProviderFailureAfterOneMinute() {
        EmailNotificationDelivery delivery = delivery(0);
        when(deliveries.findDueForUpdate(anyList(), any(LocalDateTime.class), any()))
                .thenReturn(List.of(delivery));
        when(users.findById(8L)).thenReturn(Optional.of(user()));
        doThrow(new EmailDeliveryException("provider unavailable"))
                .when(emailService).sendComposedEmail(anyString(), anyString(), anyString());

        service.processDueDeliveries();

        assertThat(delivery.getStatus()).isEqualTo(EmailDeliveryStatus.RETRY);
        assertThat(delivery.getAttemptCount()).isEqualTo(1);
        assertThat(delivery.getNextAttemptAt()).isAfter(LocalDateTime.now());
        assertThat(delivery.getLastError()).isEqualTo("provider unavailable");
    }

    @Test
    void permanentlyFailsAfterInitialAttemptAndThreeRetries() {
        EmailNotificationDelivery delivery = delivery(3);
        when(deliveries.findDueForUpdate(anyList(), any(LocalDateTime.class), any()))
                .thenReturn(List.of(delivery));
        when(users.findById(8L)).thenReturn(Optional.of(user()));
        doThrow(new EmailDeliveryException("provider unavailable"))
                .when(emailService).sendComposedEmail(anyString(), anyString(), anyString());

        service.processDueDeliveries();

        assertThat(delivery.getStatus()).isEqualTo(EmailDeliveryStatus.FAILED);
        assertThat(delivery.getAttemptCount()).isEqualTo(4);
    }

    private EmailNotificationDelivery delivery(int priorAttempts) {
        Notification notification = new Notification(
                8L, "Attendance update", "Please review attendance.");
        EmailNotificationDelivery delivery = new EmailNotificationDelivery();
        delivery.setNotification(notification);
        delivery.setUserId(8L);
        delivery.setAttemptCount(priorAttempts);
        delivery.setNextAttemptAt(LocalDateTime.now().minusMinutes(1));
        return delivery;
    }

    private User user() {
        User user = new User();
        user.setId(8L);
        user.setEmail("student@example.test");
        return user;
    }
}
