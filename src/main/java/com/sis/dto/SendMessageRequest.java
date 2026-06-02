package com.sis.dto;

public class SendMessageRequest {
    private String receiverUsername;
    private String subject;
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