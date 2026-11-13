package com.sis.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

public class CreateCourseUnitRequest {
    @NotBlank private String unitCode;
    @NotBlank private String name;
    @NotNull private Long courseId;
    @NotNull private Long teacherId;

    public String getUnitCode() { return unitCode; }
    public void setUnitCode(String unitCode) { this.unitCode = unitCode; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public Long getTeacherId() { return teacherId; }
    public void setTeacherId(Long teacherId) { this.teacherId = teacherId; }
}