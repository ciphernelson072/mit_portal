package com.sis.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class CreateCurriculumCourseRequest {
    @NotNull private Long programId;
    @NotNull private Long courseId;
    @NotNull @Min(1) private Integer semesterNumber;
    private Boolean required = true;

    public Long getProgramId() { return programId; }
    public void setProgramId(Long programId) { this.programId = programId; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public Integer getSemesterNumber() { return semesterNumber; }
    public void setSemesterNumber(Integer semesterNumber) { this.semesterNumber = semesterNumber; }
    public Boolean getRequired() { return required; }
    public void setRequired(Boolean required) { this.required = required; }
}