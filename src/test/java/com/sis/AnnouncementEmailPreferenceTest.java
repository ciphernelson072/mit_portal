package com.sis;

import com.sis.controller.AnnouncementController;
import com.sis.dto.AnnouncementDto;
import com.sis.dto.CreateAnnouncementRequest;
import com.sis.entity.AnnouncementAudience;
import com.sis.entity.Role;
import com.sis.entity.User;
import com.sis.service.EmailService;
import com.sis.service.NotificationService;
import com.sis.service.SchoolService;
import com.sis.repository.UserRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnnouncementEmailPreferenceTest {

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void optionalAnnouncementEmailFollowsSavedEmailOptIn(boolean emailEnabled) {
        SchoolService schoolService = mock(SchoolService.class);
        NotificationService notifications = mock(NotificationService.class);
        UserRepository users = mock(UserRepository.class);
        EmailService emailService = mock(EmailService.class);
        AnnouncementController controller = new AnnouncementController(schoolService, notifications, users);
        ReflectionTestUtils.setField(controller, "emailService", emailService);

        User author = user(1L, "admin", Role.ADMIN, null);
        User recipient = user(2L, "student", Role.STUDENT, "student@example.com");
        AnnouncementDto announcement = new AnnouncementDto(
                7L, "Portal update", "Updated information", null, "admin", "ALL");
        CreateAnnouncementRequest request = new CreateAnnouncementRequest();
        request.setTitle(announcement.getTitle());
        request.setContent(announcement.getContent());
        request.setAudience(AnnouncementAudience.ALL);

        when(schoolService.getUserByUsername("admin")).thenReturn(Optional.of(author));
        when(schoolService.createAnnouncement(
                announcement.getTitle(), announcement.getContent(), author, AnnouncementAudience.ALL))
                .thenReturn(announcement);
        when(users.findActiveUserIdsExcluding(1L)).thenReturn(List.of(2L));
        when(users.findById(2L)).thenReturn(Optional.of(recipient));
        when(notifications.emailNotificationsEnabled(2L)).thenReturn(emailEnabled);

        try {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("admin", "not-a-password"));
            controller.createAnnouncement(request);

            if (emailEnabled) {
                verify(emailService).sendAnnouncementNotification(
                        "student@example.com", "student", "Portal update", "Updated information");
            } else {
                verify(emailService, never()).sendAnnouncementNotification(
                        anyString(), anyString(), anyString(), anyString());
            }
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private static User user(Long id, String username, Role role, String email) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setRole(role);
        user.setEmail(email);
        return user;
    }
}
