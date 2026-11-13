package com.sis.dto;

import java.util.List;

public class BulkEmailResponse {
    private final int recipientCount;
    private final int sentCount;
    private final int failedCount;
    private final List<String> failedUsernames;

    public BulkEmailResponse(int recipientCount, int sentCount, List<String> failedUsernames) {
        this.recipientCount = recipientCount;
        this.sentCount = sentCount;
        this.failedUsernames = List.copyOf(failedUsernames);
        this.failedCount = failedUsernames.size();
    }

    public int getRecipientCount() {
        return recipientCount;
    }

    public int getSentCount() {
        return sentCount;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public List<String> getFailedUsernames() {
        return failedUsernames;
    }
}
