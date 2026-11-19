package com.sis.dto;

import java.time.LocalDateTime;

public class ServiceRequestEventDto {
    private final String eventType;
    private final String actorUsername;
    private final String fromStatus;
    private final String toStatus;
    private final String fromAssigneeUsername;
    private final String toAssigneeUsername;
    private final LocalDateTime createdAt;

    public ServiceRequestEventDto(String eventType, String actorUsername, String fromStatus, String toStatus,
                                  String fromAssigneeUsername, String toAssigneeUsername,
                                  LocalDateTime createdAt) {
        this.eventType = eventType;
        this.actorUsername = actorUsername;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.fromAssigneeUsername = fromAssigneeUsername;
        this.toAssigneeUsername = toAssigneeUsername;
        this.createdAt = createdAt;
    }

    public String getEventType() { return eventType; }
    public String getActorUsername() { return actorUsername; }
    public String getFromStatus() { return fromStatus; }
    public String getToStatus() { return toStatus; }
    public String getFromAssigneeUsername() { return fromAssigneeUsername; }
    public String getToAssigneeUsername() { return toAssigneeUsername; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
