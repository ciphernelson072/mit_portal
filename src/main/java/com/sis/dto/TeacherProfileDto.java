package com.sis.dto;

import java.util.Set;

public class TeacherProfileDto {
    private Long id;
    private String username;
    private String fullName;
    private Set<String> courses;

    public TeacherProfileDto() {
    }

    public TeacherProfileDto(Long id, String username, String fullName, Set<String> courses) {
        this.id = id;
        this.username = username;
        this.fullName = fullName;
        this.courses = courses;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public Set<String> getCourses() {
        return courses;
    }

    public void setCourses(Set<String> courses) {
        this.courses = courses;
    }
}