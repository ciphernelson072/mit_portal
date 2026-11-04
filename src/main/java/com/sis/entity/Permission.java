package com.sis.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "permissions", uniqueConstraints = @UniqueConstraint(name = "uk_permission_code", columnNames = "code"))
public class Permission {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 80) private String code;
    @Column(nullable = false, length = 255) private String description;
    @Column(nullable = false) private Boolean active = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
}