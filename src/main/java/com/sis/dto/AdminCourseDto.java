package com.sis.dto;

public class AdminCourseDto {
    private Long id;
    private String name;
    private String teacherFullName;

    public AdminCourseDto() {
    }

    public AdminCourseDto(Long id, String name, String teacherFullName) {
        this.id = id;
        this.name = name;
        this.teacherFullName = teacherFullName;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTeacherFullName() {
        return teacherFullName;
    }

    public void setTeacherFullName(String teacherFullName) {
        this.teacherFullName = teacherFullName;
    }
}