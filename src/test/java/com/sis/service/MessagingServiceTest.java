package com.sis.service;

import com.sis.entity.Announcement;
import com.sis.entity.Message;
import com.sis.entity.User;
import com.sis.repository.AnnouncementRepository;
import com.sis.repository.MessageRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessagingServiceTest {

    @Test
    void publicAnnouncementsAreOrderedAndHandleLegacyRowsWithoutAuthors() {
        AnnouncementRepository announcements = mock(AnnouncementRepository.class);
        MessagingService service = new MessagingService(announcements, mock(MessageRepository.class));
        Announcement legacyAnnouncement = new Announcement();
        legacyAnnouncement.setTitle("Legacy notice");
        legacyAnnouncement.setContent("Notice content");
        legacyAnnouncement.setCreatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
        when(announcements.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(legacyAnnouncement));

        List<Map<String, Object>> result = service.getPublicAnnouncements();

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("title", "Legacy notice")
                .containsEntry("authorName", "System");
        verify(announcements).findAllByOrderByCreatedAtDesc();
    }

    @Test
    void authenticatedAnnouncementListHandlesLegacyRowsWithoutAuthors() {
        AnnouncementRepository announcements = mock(AnnouncementRepository.class);
        MessagingService service = new MessagingService(announcements, mock(MessageRepository.class));
        Announcement legacyAnnouncement = new Announcement();
        legacyAnnouncement.setTitle("Legacy notice");
        legacyAnnouncement.setContent("Notice content");
        legacyAnnouncement.setCreatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
        when(announcements.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(legacyAnnouncement));

        var result = service.listAnnouncements();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAuthorName()).isEqualTo("System");
        verify(announcements).findAllByOrderByCreatedAtDesc();
    }

    @Test
    void receiverCanMarkOnlyTheirOwnMessageRead() {
        MessageRepository messages = mock(MessageRepository.class);
        MessagingService service = new MessagingService(mock(AnnouncementRepository.class), messages);
        User sender = new User("sender", "password", com.sis.entity.Role.STUDENT);
        User receiver = new User("receiver", "password", com.sis.entity.Role.STUDENT);
        receiver.setId(42L);
        Message message = new Message(sender, receiver, "Subject", "Content");
        when(messages.findByIdAndReceiverId(7L, 42L)).thenReturn(Optional.of(message));

        assertThat(service.markMessageRead(7L, 42L)).isTrue();
        assertThat(message.isRead()).isTrue();
        verify(messages).save(message);
    }

    @Test
    void markReadRejectsMissingOwnershipAndRejectsNullUser() {
        MessageRepository messages = mock(MessageRepository.class);
        MessagingService service = new MessagingService(mock(AnnouncementRepository.class), messages);

        assertThat(service.markMessageRead(7L, 99L)).isFalse();
        assertThat(service.markMessageRead(7L, null)).isFalse();
        verify(messages, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any(Message.class));
    }
}
