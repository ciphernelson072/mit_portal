package com.sis.service;

import com.sis.dto.BulkEmailRequest;
import com.sis.dto.BulkEmailResponse;
import com.sis.entity.Role;
import com.sis.entity.Student;
import com.sis.entity.Teacher;
import com.sis.entity.User;
import com.sis.repository.StudentRepository;
import com.sis.repository.TeacherRepository;
import com.sis.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyIterable;

class AdminEmailCampaignServiceTest {
    private final UserRepository userRepository = mock(UserRepository.class);
    private final StudentRepository studentRepository = mock(StudentRepository.class);
    private final TeacherRepository teacherRepository = mock(TeacherRepository.class);
    private final EmailService emailService = mock(EmailService.class);
    private final AdminEmailCampaignService campaignService =
            new AdminEmailCampaignService(userRepository, studentRepository, teacherRepository, emailService);

    @Test
    void recipientPickerIncludesOnlyActiveAccountsWithValidEmailAddresses() {
        User valid = user(1L, "admin", "admin@example.test", true);
        User noEmail = user(2L, "no-email", null, true);
        User malformed = user(3L, "malformed", "not-an-email", true);
        User inactive = user(4L, "inactive", "inactive@example.test", false);
        when(userRepository.findByIsActiveTrueOrderByIdAsc())
                .thenReturn(List.of(valid, noEmail, malformed, inactive));
        Student profile = new Student(noEmail, "No Email", "Grade 10");
        profile.setEmail("profile@example.test");
        when(studentRepository.findAllWithUserByUserIdIn(Set.of(1L, 2L, 3L, 4L)))
                .thenReturn(List.of(profile));
        when(teacherRepository.findAllWithUserByUserIdIn(Set.of(1L, 2L, 3L, 4L)))
                .thenReturn(List.of());

        List<?> recipients = campaignService.listRecipients();

        assertEquals(2, recipients.size());
        verify(userRepository).findByIsActiveTrueOrderByIdAsc();
    }

    @Test
    void sendsSeparateMessagesToSelectedUsers() {
        User first = user(1L, "first-user", "first@example.test", true);
        User second = user(2L, "second-user", "second@example.test", true);
        when(studentRepository.findAllWithUserByUserIdIn(Set.of(1L, 2L))).thenReturn(List.of());
        when(teacherRepository.findAllWithUserByUserIdIn(Set.of(1L, 2L))).thenReturn(List.of());
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of(first, second));

        BulkEmailResponse response = campaignService.sendCampaign(request(List.of(1L, 2L)));

        assertEquals(2, response.getRecipientCount());
        assertEquals(2, response.getSentCount());
        assertEquals(0, response.getFailedCount());
        verify(emailService).sendComposedEmail("first@example.test", "Portal update", "Hello users.");
        verify(emailService).sendComposedEmail("second@example.test", "Portal update", "Hello users.");
    }

    @Test
    void continuesSendingAndReportsPartialSmtpFailures() {
        User first = user(1L, "first-user", "first@example.test", true);
        User second = user(2L, "second-user", "second@example.test", true);
        when(studentRepository.findAllWithUserByUserIdIn(Set.of(1L, 2L))).thenReturn(List.of());
        when(teacherRepository.findAllWithUserByUserIdIn(Set.of(1L, 2L))).thenReturn(List.of());
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of(first, second));
        doThrow(new IllegalStateException("SMTP failure"))
                .when(emailService).sendComposedEmail("first@example.test", "Portal update", "Hello users.");

        BulkEmailResponse response = campaignService.sendCampaign(request(List.of(1L, 2L)));

        assertEquals(1, response.getSentCount());
        assertEquals(1, response.getFailedCount());
        assertEquals(List.of("first-user"), response.getFailedUsernames());
        verify(emailService).sendComposedEmail("second@example.test", "Portal update", "Hello users.");
    }

    @Test
    void rejectsInactiveOrInvalidAccountsBeforeSendingAnyMessage() {
        User inactive = user(1L, "inactive-user", "inactive@example.test", false);
        when(studentRepository.findAllWithUserByUserIdIn(Set.of(1L))).thenReturn(List.of());
        when(teacherRepository.findAllWithUserByUserIdIn(Set.of(1L))).thenReturn(List.of());
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of(inactive));

        assertThrows(IllegalArgumentException.class,
                () -> campaignService.sendCampaign(request(List.of(1L))));

        verify(emailService, never()).sendComposedEmail(
                "inactive@example.test", "Portal update", "Hello users.");
    }

    private BulkEmailRequest request(List<Long> recipientIds) {
        BulkEmailRequest request = new BulkEmailRequest();
        request.setRecipientIds(recipientIds);
        request.setSubject("Portal update");
        request.setContent("Hello users.");
        return request;
    }

    private User user(Long id, String username, String email, boolean active) {
        User user = new User(username, "encoded", Role.ADMIN);
        user.setId(id);
        user.setEmail(email);
        user.setIsActive(active);
        return user;
    }
}
