package com.sis.service;

import com.sis.entity.Notification;
import com.sis.entity.NotificationCategory;
import com.sis.entity.UserPreference;
import com.sis.repository.NotificationRepository;
import com.sis.repository.UserPreferenceRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NotificationServiceTest {

    @Test
    void filtersNotificationsBySavedCategoryPreference() {
        NotificationRepository repository = mock(NotificationRepository.class);
        UserPreferenceRepository preferences = mock(UserPreferenceRepository.class);
        NotificationService service = new NotificationService(repository, preferences);

        UserPreference preference = new UserPreference();
        preference.setUserId(7L);
        preference.setSettingsJson("{\"notifications\":[true,false,true,false]}");
        when(preferences.findByUserId(7L)).thenReturn(Optional.of(preference));

        Notification assignment = new Notification(7L, "Assignment", "New work", NotificationCategory.ASSIGNMENT);
        Notification grade = new Notification(7L, "Grade", "Result released", NotificationCategory.GRADE);
        Notification system = new Notification(7L, "System", "Maintenance", NotificationCategory.SYSTEM);
        Notification payment = new Notification(7L, "Payment", "Received", NotificationCategory.PAYMENT);
        when(repository.findByUserIdOrderByCreatedAtDesc(7L)).thenReturn(List.of(assignment, grade, system, payment));

        List<Notification> visible = service.listVisibleForUser(7L);

        List<NotificationCategory> categories = new java.util.ArrayList<>();
        for (Notification notification : visible) {
            categories.add(notification.getCategory());
        }

        assertEquals(2, visible.size());
        assertEquals(List.of(NotificationCategory.ASSIGNMENT, NotificationCategory.SYSTEM), categories);
    }
}
