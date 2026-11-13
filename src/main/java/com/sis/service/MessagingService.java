package com.sis.service;

import com.sis.dto.AnnouncementDto;
import com.sis.dto.MessageDto;
import com.sis.entity.Announcement;
import com.sis.entity.Message;
import com.sis.entity.User;
import com.sis.repository.AnnouncementRepository;
import com.sis.repository.MessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Announcements and direct messages - split out of the former monolithic SchoolService. */
@Service
@Transactional
public class MessagingService {

    private final AnnouncementRepository announcementRepository;
    private final MessageRepository messageRepository;

    public MessagingService(AnnouncementRepository announcementRepository, MessageRepository messageRepository) {
        this.announcementRepository = announcementRepository;
        this.messageRepository = messageRepository;
    }

    // --- Announcements ---

    public List<AnnouncementDto> listAnnouncements() {
        return announcementRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(ann -> new AnnouncementDto(
                        ann.getId(),
                        ann.getTitle(),
                        ann.getContent(),
                        ann.getCreatedAt(),
                        authorUsername(ann)))
                .collect(Collectors.toList());
    }

    public AnnouncementDto createAnnouncement(String title, String content, User author) {
        Announcement saved = announcementRepository.save(new Announcement(title, content, author));
        return new AnnouncementDto(saved.getId(), saved.getTitle(), saved.getContent(), saved.getCreatedAt(), saved.getAuthor().getUsername());
    }

    public List<Map<String, Object>> getPublicAnnouncements() {
        return announcementRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(ann -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("title", ann.getTitle());
                    map.put("content", ann.getContent());
                    map.put("createdAt", ann.getCreatedAt());
                    map.put("authorName", authorUsername(ann));
                    return map;
                })
                .collect(Collectors.toList());
    }

    private String authorUsername(Announcement announcement) {
        return announcement.getAuthor() == null ? "System" : announcement.getAuthor().getUsername();
    }

    // --- Messages ---

    public Message sendMessage(User sender, User receiver, String subject, String content) {
        return messageRepository.save(new Message(sender, receiver, subject, content));
    }

    public List<Message> getReceivedMessages(Long userId) {
        return userId == null ? Collections.emptyList() : messageRepository.findByReceiverIdOrderBySentAtDesc(userId);
    }

    public List<Message> getSentMessages(Long userId) {
        return userId == null ? Collections.emptyList() : messageRepository.findBySenderIdOrderBySentAtDesc(userId);
    }

    public boolean markMessageRead(Long messageId, Long userId) {
        if (messageId == null || userId == null) {
            return false;
        }
        return messageRepository.findByIdAndReceiverId(messageId, userId)
                .map(message -> {
            message.setRead(true);
            messageRepository.save(message);
            return true;
        }).orElse(false);
    }

    public MessageDto toMessageDto(Message message) {
        MessageDto dto = new MessageDto();
        dto.setId(message.getId());
        dto.setSenderUsername(message.getSender() != null ? message.getSender().getUsername() : null);
        dto.setReceiverUsername(message.getReceiver() != null ? message.getReceiver().getUsername() : null);
        dto.setSubject(message.getSubject());
        dto.setContent(message.getContent());
        dto.setSentAt(message.getSentAt());
        dto.setRead(message.isRead());
        return dto;
    }

    public List<MessageDto> getReceivedMessageDtos(Long userId) {
        return getReceivedMessages(userId).stream().map(this::toMessageDto).collect(Collectors.toList());
    }

    public List<MessageDto> getSentMessageDtos(Long userId) {
        return getSentMessages(userId).stream().map(this::toMessageDto).collect(Collectors.toList());
    }
}
