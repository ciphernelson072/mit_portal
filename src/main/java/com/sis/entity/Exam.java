package com.sis.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "exams")
public class Exam {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long courseId;
    @Column(nullable = false) private String title;
    @Column(nullable = false) private LocalDateTime startsAt;
    @Column(nullable = false) private String venue;
    public Exam() {}
    public Long getId() { return id; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long value) { courseId = value; }
    public String getTitle() { return title; }
    public void setTitle(String value) { title = value; }
    public LocalDateTime getStartsAt() { return startsAt; }
    public void setStartsAt(LocalDateTime value) { startsAt = value; }
    public String getVenue() { return venue; }
    public void setVenue(String value) { venue = value; }
}
