package com.sis.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class CreateCourseSectionRequest {
    @NotNull private Long courseId;
    @NotNull private Long academicTermId;
    @NotBlank @Size(max = 30) private String sectionCode;
    @NotNull @Min(1) private Integer capacity;

    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public Long getAcademicTermId() { return academicTermId; }
    public void setAcademicTermId(Long academicTermId) { this.academicTermId = academicTermId; }
    public String getSectionCode() { return sectionCode; }
    public void setSectionCode(String sectionCode) { this.sectionCode = sectionCode; }
    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }
}