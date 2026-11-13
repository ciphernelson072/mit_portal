package com.sis.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class SendMessageRequest {

    @NotBlank(message = "Receiver username is required")
    private String receiverUsername;

    @NotBlank(message = "Subject is required")
    @Size(max = 200, message = "Subject must be at most 200 characters")
    private String subject;

    @NotBlank(message = "Content is required")
    @Size(max = 5000, message = "Content must be at most 5000 characters")
    private String content;

    public SendMessageRequest() {}

    public SendMessageRequest(String receiverUsername, String subject, String content) {
        this.receiverUsername = receiverUsername;
        this.subject = subject;
        this.content = content;
    }

    public String getReceiverUsername() { return receiverUsername; }
    public void setReceiverUsername(String receiverUsername) { this.receiverUsername = receiverUsername; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
