package com.sis.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;

public class CreateExamRequest {
    @NotNull private Long courseId;
    @NotBlank @Size(max = 255) private String title;
    @NotNull private LocalDateTime startsAt;
    @NotBlank @Size(max = 255) private String venue;

    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public LocalDateTime getStartsAt() { return startsAt; }
    public void setStartsAt(LocalDateTime startsAt) { this.startsAt = startsAt; }
    public String getVenue() { return venue; }
    public void setVenue(String venue) { this.venue = venue; }
}
