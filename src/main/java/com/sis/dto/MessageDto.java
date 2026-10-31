package com.sis.dto;

import java.time.LocalDateTime;

/**
 * Read-only representation of a Message returned to API clients.
 * Exposes sender/receiver usernames only - never the underlying User entity
 * (which carries the password hash, even if write-only-annotated for Jackson).
 */
public class MessageDto {
    private Long id;
    private String senderUsername;
    private String receiverUsername;
    private String subject;
    private String content;
    private LocalDateTime sentAt;
    private boolean read;

    public MessageDto() {}

    public MessageDto(Long id, String senderUsername, String receiverUsername, String subject, String content,
                      LocalDateTime sentAt, boolean read) {
        this.id = id;
        this.senderUsername = senderUsername;
        this.receiverUsername = receiverUsername;
        this.subject = subject;
        this.content = content;
        this.sentAt = sentAt;
        this.read = read;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSenderUsername() { return senderUsername; }
    public void setSenderUsername(String senderUsername) { this.senderUsername = senderUsername; }
    public String getReceiverUsername() { return receiverUsername; }
    public void setReceiverUsername(String receiverUsername) { this.receiverUsername = receiverUsername; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }
    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }
}
