package com.sis.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "service_request_events", indexes = {
        @Index(name = "idx_service_request_event_request_created", columnList = "service_request_id, created_at")
})
public class ServiceRequestEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_request_id", nullable = false)
    private ServiceRequest serviceRequest;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "actor_username", nullable = false, length = 255)
    private String actorUsername;

    @Column(name = "event_type", nullable = false, length = 30)
    private String eventType;

    @Column(name = "from_status", length = 30)
    private String fromStatus;

    @Column(name = "to_status", length = 30)
    private String toStatus;

    @Column(name = "from_assignee_id")
    private Long fromAssigneeId;

    @Column(name = "to_assignee_id")
    private Long toAssigneeId;

    @Column(name = "from_assignee_username", length = 255)
    private String fromAssigneeUsername;

    @Column(name = "to_assignee_username", length = 255)
    private String toAssigneeUsername;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected ServiceRequestEvent() {
    }

    public ServiceRequestEvent(ServiceRequest serviceRequest, User actor, String eventType,
                               String fromStatus, String toStatus, User fromAssignee, User toAssignee) {
        this.serviceRequest = serviceRequest;
        this.actorUserId = actor.getId();
        this.actorUsername = actor.getUsername();
        this.eventType = eventType;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.fromAssigneeId = fromAssignee == null ? null : fromAssignee.getId();
        this.toAssigneeId = toAssignee == null ? null : toAssignee.getId();
        this.fromAssigneeUsername = fromAssignee == null ? null : fromAssignee.getUsername();
        this.toAssigneeUsername = toAssignee == null ? null : toAssignee.getUsername();
    }

    public Long getId() { return id; }
    public ServiceRequest getServiceRequest() { return serviceRequest; }
    public void setServiceRequest(ServiceRequest serviceRequest) { this.serviceRequest = serviceRequest; }
    public Long getActorUserId() { return actorUserId; }
    public String getActorUsername() { return actorUsername; }
    public String getEventType() { return eventType; }
    public String getFromStatus() { return fromStatus; }
    public String getToStatus() { return toStatus; }
    public Long getFromAssigneeId() { return fromAssigneeId; }
    public Long getToAssigneeId() { return toAssigneeId; }
    public String getFromAssigneeUsername() { return fromAssigneeUsername; }
    public String getToAssigneeUsername() { return toAssigneeUsername; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
