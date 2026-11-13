package com.sis.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;

public class CreateCourseRequest {

    @NotBlank(message = "Course name is required")
    private String name;

    @NotBlank(message = "Course code is required")
    private String courseCode;

    @NotNull(message = "Teacher ID is required")
    private Long teacherId;

    private String semester;

    private Integer credits;

    private Boolean core;

    @NotNull(message = "Course category is required")
    private Long categoryId;

    private List<Long> studentIds;

    public CreateCourseRequest() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCourseCode() { return courseCode; }
    public void setCourseCode(String courseCode) { this.courseCode = courseCode; }

    public Long getTeacherId() {
        return teacherId;
    }

    public void setTeacherId(Long teacherId) {
        this.teacherId = teacherId;
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

    public Boolean getCore() { return core; }
    public void setCore(Boolean core) { this.core = core; }

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }

    public List<Long> getStudentIds() {
        return studentIds;
    }

    public void setStudentIds(List<Long> studentIds) {
        this.studentIds = studentIds;
    }
}
