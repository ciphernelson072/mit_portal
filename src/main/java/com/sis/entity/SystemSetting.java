package com.sis.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "system_settings")
public class SystemSetting {
    @Id @Column(length = 100) private String settingKey;
    @Column(length = 4000, nullable = false) private String settingValue;
    public SystemSetting() {}
    public SystemSetting(String key, String value) { settingKey = key; settingValue = value; }
    public String getSettingKey() { return settingKey; }
    public String getSettingValue() { return settingValue; }
    public void setSettingValue(String value) { settingValue = value; }
}
