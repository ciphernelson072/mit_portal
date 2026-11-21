package com.sis.service;

import com.sis.entity.Notification;
import com.sis.entity.NotificationCategory;
import com.sis.repository.NotificationRepository;
import com.sis.repository.UserPreferenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class NotificationService {

    private final NotificationRepository repository;
    private final PreferencePolicyService preferencePolicyService;
    private final EmailNotificationDeliveryService emailDeliveries;

    public NotificationService(
            NotificationRepository repository,
            PreferencePolicyService preferencePolicyService,
            EmailNotificationDeliveryService emailDeliveries) {
        this.repository = repository;
        this.preferencePolicyService = preferencePolicyService;
        this.emailDeliveries = emailDeliveries;
    }

    @Transactional(readOnly = true)
    public List<Notification> listForUser(Long userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public List<Notification> listVisibleForUser(Long userId) {
        List<Boolean> visibility = preferencePolicyService.notificationVisibility(userId);
        return repository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(notification -> preferencePolicyService.isNotificationCategoryVisible(
                        notification.getCategory(), visibility))
                .toList();
    }

    public boolean emailNotificationsEnabled(Long userId) {
        return preferencePolicyService.emailNotificationsEnabled(userId);
    }

    public Notification create(Long userId, String title, String message) {
        return create(userId, title, message, NotificationCategory.SYSTEM);
    }

    public Notification create(Long userId, String title, String message, NotificationCategory category) {
        return create(userId, title, message, category, true);
    }

    public Notification createInAppOnly(
            Long userId, String title, String message, NotificationCategory category) {
        return create(userId, title, message, category, false);
    }

    private Notification create(
            Long userId, String title, String message, NotificationCategory category, boolean queueEmail) {
        if (category == null) {
            throw new IllegalArgumentException("Notification category is required.");
        }
        Notification notification = repository.save(new Notification(userId, title, message, category));
        if (queueEmail && preferencePolicyService.emailNotificationsEnabled(userId)) {
            emailDeliveries.enqueue(notification);
        }
        return notification;
    }

    public boolean createAutomated(
            String automationKey, Long userId, String title, String message, NotificationCategory category) {
        if (automationKey == null || automationKey.isBlank() || automationKey.length() > 255) {
            throw new IllegalArgumentException("A valid automation notification key is required.");
        }
        if (category == null) {
            throw new IllegalArgumentException("Notification category is required.");
        }
        if (repository.findByAutomationKey(automationKey).isPresent()) {
            return false;
        }
        Notification notification = new Notification(userId, title, message, category);
        notification.setAutomationKey(automationKey);
        Notification saved = repository.save(notification);
        if (preferencePolicyService.emailNotificationsEnabled(userId)) {
            emailDeliveries.enqueue(saved);
        }
        return true;
    }

    public void markRead(Long userId, Long notificationId) {
        repository.findById(notificationId)
                .filter(notification -> notification.getUserId().equals(userId))
                .ifPresent(notification -> {
                    notification.setRead(true);
                    repository.save(notification);
                });
    }

    public void markAllRead(Long userId) {
        listForUser(userId).forEach(notification -> {
            notification.setRead(true);
            repository.save(notification);
        });
    }
}