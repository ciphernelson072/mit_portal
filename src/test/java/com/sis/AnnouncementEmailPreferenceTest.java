package com.sis;

import com.sis.controller.AnnouncementController;
import com.sis.dto.AnnouncementDto;
import com.sis.dto.CreateAnnouncementRequest;
import com.sis.entity.AnnouncementAudience;
import com.sis.entity.NotificationCategory;
import com.sis.entity.Role;
import com.sis.entity.Student;
import com.sis.entity.User;
import com.sis.exception.EmailDeliveryException;
import com.sis.service.EmailService;
import com.sis.service.NotificationService;
import com.sis.service.ProfileContactEmailService;
import com.sis.service.SchoolService;
import org.junit.jupiter.api.Test;
import com.sis.repository.StudentRepository;
import com.sis.repository.TeacherRepository;
import com.sis.repository.UserRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnnouncementEmailPreferenceTest {

    @Test
    void programmeOrCourseTargetsCannotBeCombinedWithNonStudentAudience() {
        AnnouncementController controller = new AnnouncementController(
                mock(SchoolService.class), mock(NotificationService.class),
                mock(UserRepository.class), mock(ProfileContactEmailService.class));
        CreateAnnouncementRequest request = new CreateAnnouncementRequest();
        request.setTitle("Targeted notice");
        request.setContent("Details");
        request.setAudience(AnnouncementAudience.ALL);
        request.setProgramIds(List.of(11L));

        try {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("admin", "not-a-password"));
            org.junit.jupiter.api.Assertions.assertThrows(
                    IllegalArgumentException.class,
                    () -> controller.createAnnouncement(request));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void announcementDeliverySummaryReportsProviderFailures() {
        SchoolService schoolService = mock(SchoolService.class);
        NotificationService notifications = mock(NotificationService.class);
        UserRepository users = mock(UserRepository.class);
        EmailService emailService = mock(EmailService.class);
        User author = user(1L, "admin", Role.ADMIN, null);
        User recipient = user(2L, "student", Role.STUDENT, "student-account@example.com");
        StudentRepository students = mock(StudentRepository.class);
        TeacherRepository teachers = mock(TeacherRepository.class);
        Student studentProfile = new Student(recipient, "Student", "Grade 10");
        studentProfile.setEmail("student-contact@example.com");
        AnnouncementController controller = new AnnouncementController(
                schoolService, notifications, users, new ProfileContactEmailService(students, teachers));
        ReflectionTestUtils.setField(controller, "emailService", emailService);
        when(students.findAllWithUserByUserIdIn(List.of(2L))).thenReturn(List.of(studentProfile));
        when(teachers.findAllWithUserByUserIdIn(List.of(2L))).thenReturn(List.of());
        when(schoolService.getUserByUsername("admin")).thenReturn(Optional.of(author));
        when(schoolService.createAnnouncement("Notice", "Details", author, AnnouncementAudience.ALL))
                .thenReturn(new AnnouncementDto(7L, "Notice", "Details", null, "admin", "ALL"));
        when(users.findActiveUserIdsExcluding(1L)).thenReturn(List.of(2L));
        when(users.findById(2L)).thenReturn(Optional.of(recipient));
        when(notifications.emailNotificationsEnabled(2L)).thenReturn(true);
        org.mockito.Mockito.doThrow(new EmailDeliveryException("Provider unavailable"))
                .when(emailService).sendAnnouncementNotification(
                        "student-contact@example.com", "student", "Notice", "Details");

        CreateAnnouncementRequest request = new CreateAnnouncementRequest();
        request.setTitle("Notice");
        request.setContent("Details");
        request.setAudience(AnnouncementAudience.ALL);

        try {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("admin", "not-a-password"));
            AnnouncementDto result = controller.createAnnouncement(request).getBody();

            assertThat(result.getInAppRecipientCount()).isEqualTo(1);
            assertThat(result.getEmailRecipientCount()).isEqualTo(1);
            assertThat(result.getEmailSuccessCount()).isZero();
            assertThat(result.getEmailFailureCount()).isEqualTo(1);
            assertThat(result.getEmailSkippedCount()).isZero();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @ParameterizedTest
    @CsvSource({"false,true", "true,true", "true,false"})
    void optionalAnnouncementEmailRequiresOptInAndProfileContactAddress(
            boolean emailEnabled, boolean hasContactEmail) {
        SchoolService schoolService = mock(SchoolService.class);
        NotificationService notifications = mock(NotificationService.class);
        UserRepository users = mock(UserRepository.class);
        EmailService emailService = mock(EmailService.class);

        User author = user(1L, "admin", Role.ADMIN, null);
        User recipient = user(2L, "student", Role.STUDENT, "student-account@example.com");
        StudentRepository students = mock(StudentRepository.class);
        TeacherRepository teachers = mock(TeacherRepository.class);
        Student studentProfile = new Student(recipient, "Student", "Grade 10");
        studentProfile.setEmail("student-contact@example.com");
        AnnouncementController controller = new AnnouncementController(
                schoolService, notifications, users, new ProfileContactEmailService(students, teachers));
        ReflectionTestUtils.setField(controller, "emailService", emailService);
        when(students.findAllWithUserByUserIdIn(List.of(2L)))
                .thenReturn(hasContactEmail ? List.of(studentProfile) : List.of());
        when(teachers.findAllWithUserByUserIdIn(List.of(2L))).thenReturn(List.of());
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
            AnnouncementDto result = controller.createAnnouncement(request).getBody();

            verify(notifications).createInAppOnly(
                    2L, "New announcement", announcement.getTitle(), NotificationCategory.ANNOUNCEMENT);
            if (emailEnabled && hasContactEmail) {
                verify(emailService).sendAnnouncementNotification(
                        "student-contact@example.com", "student", "Portal update", "Updated information");
                assertThat(result.getEmailSuccessCount()).isEqualTo(1);
                assertThat(result.getEmailFailureCount()).isZero();
                assertThat(result.getEmailSkippedCount()).isZero();
            } else {
                verify(emailService, never()).sendAnnouncementNotification(
                        anyString(), anyString(), anyString(), anyString());
                assertThat(result.getEmailSuccessCount()).isZero();
                assertThat(result.getEmailFailureCount()).isZero();
                assertThat(result.getEmailSkippedCount()).isEqualTo(1);
            }
            assertThat(result.getInAppRecipientCount()).isEqualTo(1);
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
        user.setIsActive(true);
        return user;
    }
}
