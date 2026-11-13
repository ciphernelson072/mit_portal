package com.sis.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;

public class BulkEmailRequest {
    @NotEmpty
    @Size(max = 450, message = "A campaign can include at most 450 recipients")
    private List<@NotNull Long> recipientIds;

    @NotBlank
    @Size(max = 200, message = "Subject must be at most 200 characters")
    private String subject;

    @NotBlank
    @Size(max = 10000, message = "Message must be at most 10000 characters")
    private String content;

    public List<Long> getRecipientIds() {
        return recipientIds;
    }

    public void setRecipientIds(List<Long> recipientIds) {
        this.recipientIds = recipientIds;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
