package com.sis.dto;

import javax.validation.constraints.NotNull;

public class CreatePrerequisiteRequest {
    @NotNull private Long courseId;
    @NotNull private Long prerequisiteCourseId;

    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public Long getPrerequisiteCourseId() { return prerequisiteCourseId; }
    public void setPrerequisiteCourseId(Long prerequisiteCourseId) { this.prerequisiteCourseId = prerequisiteCourseId; }
}