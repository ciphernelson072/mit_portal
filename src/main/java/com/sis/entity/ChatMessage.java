package com.sis.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "chat_messages")
public class ChatMessage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "student_id", nullable = false) private Long studentId;
    @Column(nullable = false, length = 5000) private String content;
    @Column(nullable = false) private String sender = "STUDENT";
    @Column(nullable = false) private LocalDateTime createdAt = LocalDateTime.now();
    public ChatMessage() {}
    public ChatMessage(Long studentId, String content, String sender) { this.studentId = studentId; this.content = content; this.sender = sender; }
    public Long getId() { return id; }
    public Long getStudentId() { return studentId; }
    public String getContent() { return content; }
    public String getSender() { return sender; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
