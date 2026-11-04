package com.sis.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "user_preferences", uniqueConstraints = @UniqueConstraint(columnNames = "user_id"))
public class UserPreference {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(length = 20) private String language = "English";
    @Column(length = 20) private String theme = "light";
    @Column(length = 4000) private String settingsJson = "{}";
    public UserPreference() {}
    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long value) { userId = value; }
    public String getLanguage() { return language; }
    public void setLanguage(String value) { language = value; }
    public String getTheme() { return theme; }
    public void setTheme(String value) { theme = value; }
    public String getSettingsJson() { return settingsJson; }
    public void setSettingsJson(String value) { settingsJson = value; }
}
