package com.sis.dto;

import jakarta.validation.constraints.NotNull;

public class AllocateHostelRequest {
    @NotNull private Long applicationId;
    @NotNull private Long hostelRoomId;
    public Long getApplicationId() { return applicationId; }
    public void setApplicationId(Long applicationId) { this.applicationId = applicationId; }
    public Long getHostelRoomId() { return hostelRoomId; }
    public void setHostelRoomId(Long hostelRoomId) { this.hostelRoomId = hostelRoomId; }
}