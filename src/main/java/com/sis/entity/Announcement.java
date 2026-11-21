package com.sis.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "announcements")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Announcement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    private User author;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AnnouncementAudience audience = AnnouncementAudience.ALL;

    @ManyToMany
    @JoinTable(name = "announcement_program_targets",
            joinColumns = @JoinColumn(name = "announcement_id"),
            inverseJoinColumns = @JoinColumn(name = "program_id"))
    private Set<Program> programs = new HashSet<>();

    @ManyToMany
    @JoinTable(name = "announcement_course_targets",
            joinColumns = @JoinColumn(name = "announcement_id"),
            inverseJoinColumns = @JoinColumn(name = "course_id"))
    private Set<Course> courses = new HashSet<>();

    public Announcement() {
    }

    public Announcement(String title, String content, User author) {
        this.title = title;
        this.content = content;
        this.author = author;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public User getAuthor() {
        return author;
    }

    public void setAuthor(User author) {
        this.author = author;
    }

    public AnnouncementAudience getAudience() {
        return audience;
    }

    public void setAudience(AnnouncementAudience audience) {
        this.audience = audience == null ? AnnouncementAudience.ALL : audience;
    }

    public Set<Program> getPrograms() { return programs; }
    public void setPrograms(Set<Program> programs) { this.programs = programs == null ? new HashSet<>() : programs; }
    public Set<Course> getCourses() { return courses; }
    public void setCourses(Set<Course> courses) { this.courses = courses == null ? new HashSet<>() : courses; }
}