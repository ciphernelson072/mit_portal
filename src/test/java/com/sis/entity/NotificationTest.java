package com.sis.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NotificationTest {

    @Test
    void defaultsExistingNotificationCreationToSystemCategory() {
        Notification notification = new Notification(7L, "Notice", "Message");

        assertEquals(NotificationCategory.SYSTEM, notification.getCategory());
    }

    @Test
    void preservesExplicitNotificationCategory() {
        Notification notification = new Notification(
                7L, "Grade posted", "A new grade is available", NotificationCategory.GRADE);

        assertEquals(NotificationCategory.GRADE, notification.getCategory());
    }
}
