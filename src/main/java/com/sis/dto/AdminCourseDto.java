package com.sis.dto;

public class AdminCourseDto {
    private Long id;
    private String name;
    private String courseCode;
    private String category;
    private String teacherFullName;
    private String semester;
    private Integer credits;

    public AdminCourseDto() {
    }

    public AdminCourseDto(Long id, String name, String teacherFullName) {
        this.id = id;
        this.name = name;
        this.teacherFullName = teacherFullName;
    }

    public AdminCourseDto(Long id, String name, String teacherFullName, String semester, Integer credits) {
        this(id, name, teacherFullName);
        this.semester = semester;
        this.credits = credits;
    }

    public AdminCourseDto(Long id, String name, String courseCode, String category, String teacherFullName, String semester, Integer credits) {
        this(id, name, teacherFullName, semester, credits);
        this.courseCode = courseCode;
        this.category = category;
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
    public String getCourseCode() { return courseCode; }
    public void setCourseCode(String courseCode) { this.courseCode = courseCode; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getTeacherFullName() {
        return teacherFullName;
    }

    public void setTeacherFullName(String teacherFullName) {
        this.teacherFullName = teacherFullName;
    }

    public String getSemester() {
        return semester;
    }

    public void setSemester(String semester) {
        this.semester = semester;
    }

    public Integer getCredits() {
        return credits;
    }

    public void setCredits(Integer credits) {
        this.credits = credits;
    }
}