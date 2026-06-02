package com.sis.dto;

public class AdminStudentDto {
    private Long id;
    private String fullName;
    private String className;
    private String username;

    public AdminStudentDto() {
    }

    public AdminStudentDto(Long id, String fullName, String className, String username) {
        this.id = id;
        this.fullName = fullName;
        this.className = className;
        this.username = username;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}