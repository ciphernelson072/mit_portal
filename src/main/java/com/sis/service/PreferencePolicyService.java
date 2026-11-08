package com.sis.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sis.entity.NotificationCategory;
import com.sis.entity.UserPreference;
import com.sis.repository.UserPreferenceRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class PreferencePolicyService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Logger LOGGER = Logger.getLogger(PreferencePolicyService.class.getName());
    private static final List<NotificationCategory> CATEGORY_ORDER = List.of(
            NotificationCategory.ASSIGNMENT,
            NotificationCategory.GRADE,
            NotificationCategory.SYSTEM,
            NotificationCategory.PAYMENT
    );
    private static final List<Boolean> DEFAULT_NOTIFICATION_VISIBILITY = List.of(true, true, false, true);

    private final UserPreferenceRepository preferences;

    public PreferencePolicyService(UserPreferenceRepository preferences) {
        this.preferences = preferences;
    }

    public boolean isNotificationCategoryVisible(Long userId, NotificationCategory category) {
        return isNotificationCategoryVisible(category, notificationVisibility(userId));
    }

    public boolean isNotificationCategoryVisible(NotificationCategory category, List<Boolean> visibility) {
        if (category == null) {
            return true;
        }
        int index = CATEGORY_ORDER.indexOf(category);
        if (index < 0) {
            return true;
        }
        return Boolean.TRUE.equals(visibility.get(index));
    }

    public boolean emailNotificationsEnabled(Long userId) {
        if (userId == null) {
            return false;
        }
        UserPreference preference = preferences.findByUserId(userId).orElse(null);
        if (preference == null || preference.getSettingsJson() == null || preference.getSettingsJson().isBlank()) {
            return false;
        }

        try {
            Map<String, Object> settings = OBJECT_MAPPER.readValue(
                    preference.getSettingsJson(), new TypeReference<Map<String, Object>>() {});
            Object privacy = settings.get("privacy");
            if (privacy instanceof List<?> list && list.size() > 2 && list.get(2) instanceof Boolean enabled) {
                return enabled;
            }
            return false;
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            LOGGER.log(Level.WARNING,
                    "Saved email notification preference is invalid for user " + userId + "; optional email is disabled.",
                    exception);
            return false;
        }
    }

    public List<Boolean> notificationVisibility(Long userId) {
        UserPreference preference = preferences.findByUserId(userId).orElse(null);
        if (preference == null || preference.getSettingsJson() == null || preference.getSettingsJson().isBlank()) {
            return new ArrayList<>(DEFAULT_NOTIFICATION_VISIBILITY);
        }

        try {
            Map<String, Object> settings = OBJECT_MAPPER.readValue(preference.getSettingsJson(), new TypeReference<Map<String, Object>>() {});
            Object notifications = settings.get("notifications");
            if (!(notifications instanceof List<?> list)) {
                return new ArrayList<>(DEFAULT_NOTIFICATION_VISIBILITY);
            }
            List<Boolean> visibility = new ArrayList<>(DEFAULT_NOTIFICATION_VISIBILITY);
            for (int i = 0; i < CATEGORY_ORDER.size() && i < list.size(); i++) {
                Object item = list.get(i);
                if (item instanceof Boolean bool) {
                    visibility.set(i, bool);
                }
            }
            return visibility;
        } catch (Exception ignored) {
            return new ArrayList<>(DEFAULT_NOTIFICATION_VISIBILITY);
        }
    }
}
