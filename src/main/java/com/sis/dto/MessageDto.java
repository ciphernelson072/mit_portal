package com.sis.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Read-only representation of a Message returned to API clients.
 * Exposes sender/receiver usernames only - never the underlying User entity
 * (which carries the password hash, even if write-only-annotated for Jackson).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageDto {
    private Long id;
    private String senderUsername;
    private String receiverUsername;
    private String subject;
    private String content;
    private LocalDateTime sentAt;
    private boolean read;
}
