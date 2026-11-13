package com.sis.service;

import com.sis.entity.NotificationCategory;
import com.sis.entity.UserPreference;
import com.sis.repository.UserPreferenceRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PreferencePolicyServiceTest {

    @Test
    void defaultsHiddenCategoriesToTheExistingDashboardDefaults() {
        UserPreferenceRepository preferences = mock(UserPreferenceRepository.class);
        PreferencePolicyService service = new PreferencePolicyService(preferences);

        when(preferences.findByUserId(9L)).thenReturn(Optional.empty());

        assertTrue(service.isNotificationCategoryVisible(9L, NotificationCategory.ASSIGNMENT));
        assertTrue(service.isNotificationCategoryVisible(9L, NotificationCategory.GRADE));
        assertFalse(service.isNotificationCategoryVisible(9L, NotificationCategory.SYSTEM));
        assertTrue(service.isNotificationCategoryVisible(9L, NotificationCategory.PAYMENT));
    }

    @Test
    void respectsSavedNotificationVisibilityPreferences() {
        UserPreferenceRepository preferences = mock(UserPreferenceRepository.class);
        PreferencePolicyService service = new PreferencePolicyService(preferences);

        UserPreference preference = new UserPreference();
        preference.setUserId(11L);
        preference.setSettingsJson("{\"notifications\":[false,true,true,false]}");
        when(preferences.findByUserId(11L)).thenReturn(Optional.of(preference));

        assertFalse(service.isNotificationCategoryVisible(11L, NotificationCategory.ASSIGNMENT));
        assertTrue(service.isNotificationCategoryVisible(11L, NotificationCategory.GRADE));
        assertTrue(service.isNotificationCategoryVisible(11L, NotificationCategory.SYSTEM));
        assertFalse(service.isNotificationCategoryVisible(11L, NotificationCategory.PAYMENT));
    }
}
