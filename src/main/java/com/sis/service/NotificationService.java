package com.sis.service;

import com.sis.entity.Notification;
import com.sis.entity.NotificationCategory;
import com.sis.repository.NotificationRepository;
import com.sis.repository.UserPreferenceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class NotificationService {

    @Autowired
    private NotificationRepository repository;

    @Autowired
    private PreferencePolicyService preferencePolicyService;

    protected NotificationService() {
    }

    NotificationService(NotificationRepository repository, UserPreferenceRepository userPreferenceRepository) {
        this.repository = repository;
        this.preferencePolicyService = new PreferencePolicyService(userPreferenceRepository);
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
        if (category == null) {
            throw new IllegalArgumentException("Notification category is required.");
        }
        return repository.save(new Notification(userId, title, message, category));
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