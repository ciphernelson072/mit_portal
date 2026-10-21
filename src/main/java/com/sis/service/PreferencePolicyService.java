package com.sis.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sis.entity.NotificationCategory;
import com.sis.entity.UserPreference;
import com.sis.repository.UserPreferenceRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class PreferencePolicyService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
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
        if (category == null) {
            return true;
        }
        int index = CATEGORY_ORDER.indexOf(category);
        if (index < 0) {
            return true;
        }
        List<Boolean> visibility = notificationVisibility(userId);
        return Boolean.TRUE.equals(visibility.get(index));
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
