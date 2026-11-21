package com.sis.dto;

import com.sis.entity.AnnouncementAudience;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;

public class CreateAnnouncementRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 200, message = "Title must be at most 200 characters")
    private String title;

    @NotBlank(message = "Content is required")
    @Size(max = 5000, message = "Content must be at most 5000 characters")
    private String content;

    private AnnouncementAudience audience = AnnouncementAudience.ALL;
    private List<Long> programIds = new ArrayList<>();
    private List<Long> courseIds = new ArrayList<>();

    public CreateAnnouncementRequest() {
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public AnnouncementAudience getAudience() {
        return audience;
    }

    public void setAudience(AnnouncementAudience audience) {
        this.audience = audience == null ? AnnouncementAudience.ALL : audience;
    }

    public List<Long> getProgramIds() { return programIds; }
    public void setProgramIds(List<Long> programIds) { this.programIds = programIds == null ? new ArrayList<>() : programIds; }
    public List<Long> getCourseIds() { return courseIds; }
    public void setCourseIds(List<Long> courseIds) { this.courseIds = courseIds == null ? new ArrayList<>() : courseIds; }
}
