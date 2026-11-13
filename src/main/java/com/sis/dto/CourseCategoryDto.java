package com.sis.dto;

public class CourseCategoryDto {
    private Long id;
    private String code;
    private String name;
    private boolean active;

    public CourseCategoryDto() {}
    public CourseCategoryDto(Long id, String code, String name, boolean active) {
        this.id = id; this.code = code; this.name = name; this.active = active;
    }
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}