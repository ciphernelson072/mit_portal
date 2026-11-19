package com.sis.dto;

public class ServiceRequestAssigneeDto {
    private final Long id;
    private final String username;

    public ServiceRequestAssigneeDto(Long id, String username) {
        this.id = id;
        this.username = username;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
}
