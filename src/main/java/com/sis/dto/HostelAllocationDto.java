package com.sis.dto;

import java.time.LocalDateTime;

public class HostelAllocationDto {
    private final Long id;
    private final Long applicationId;
    private final String status;
    private final LocalDateTime allocatedAt;
    private final String roomCode;
    private final String building;

    public HostelAllocationDto(Long id, Long applicationId, String status, LocalDateTime allocatedAt,
                               String roomCode, String building) {
        this.id = id;
        this.applicationId = applicationId;
        this.status = status;
        this.allocatedAt = allocatedAt;
        this.roomCode = roomCode;
        this.building = building;
    }

    public Long getId() { return id; }
    public Long getApplicationId() { return applicationId; }
    public String getStatus() { return status; }
    public LocalDateTime getAllocatedAt() { return allocatedAt; }
    public String getRoomCode() { return roomCode; }
    public String getBuilding() { return building; }
}
