package com.sis.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import java.time.LocalTime;

public class CreateTimetableRequest {
    @NotNull
    private Long courseId;

    @NotNull
    private Long academicTermId;

    @NotBlank
    @Size(max = 20)
    @Pattern(regexp = "(?i)^\\s*(MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY)\\s*$")
    private String dayOfWeek;

    @NotNull
    private LocalTime startsAt;

    @NotNull
    private LocalTime endsAt;

    @NotBlank
    @Size(max = 255)
    private String venue;

    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public Long getAcademicTermId() { return academicTermId; }
    public void setAcademicTermId(Long academicTermId) { this.academicTermId = academicTermId; }
    public String getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(String dayOfWeek) { this.dayOfWeek = dayOfWeek; }
    public LocalTime getStartsAt() { return startsAt; }
    public void setStartsAt(LocalTime startsAt) { this.startsAt = startsAt; }
    public LocalTime getEndsAt() { return endsAt; }
    public void setEndsAt(LocalTime endsAt) { this.endsAt = endsAt; }
    public String getVenue() { return venue; }
    public void setVenue(String venue) { this.venue = venue; }
}
