package com.sis.dto;

public class AdminEmailRecipientDto {
    private final Long id;
    private final String username;
    private final String role;
    private final String email;

    public AdminEmailRecipientDto(Long id, String username, String role, String email) {
        this.id = id;
        this.username = username;
        this.role = role;
        this.email = email;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getRole() {
        return role;
    }

    public String getEmail() {
        return email;
    }
}
