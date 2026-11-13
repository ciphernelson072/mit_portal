package com.sis.dto;

public class ResearchSupervisorDto {
    private final Long userId;
    private final String fullName;
    private final String department;

    public ResearchSupervisorDto(Long userId, String fullName, String department) {
        this.userId = userId;
        this.fullName = fullName;
        this.department = department;
    }

    public Long getUserId() { return userId; }
    public String getFullName() { return fullName; }
    public String getDepartment() { return department; }
}
