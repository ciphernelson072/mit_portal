package com.sis.entity;

import javax.persistence.*;
import java.time.LocalTime;

@Entity
@Table(name = "timetable_entries")
public class TimetableEntry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "course_id", nullable = false) private Long courseId;
    @Column(name = "academic_term_id")
    private Long academicTermId;
    @Column(nullable = false) private String dayOfWeek;
    @Column(nullable = false) private LocalTime startsAt;
    @Column(nullable = false) private LocalTime endsAt;
    @Column(nullable = false) private String venue;
    public TimetableEntry() {}
    public Long getId() { return id; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long value) { courseId = value; }
    public Long getAcademicTermId() { return academicTermId; }
    public void setAcademicTermId(Long academicTermId) { this.academicTermId = academicTermId; }
    public String getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(String value) { dayOfWeek = value; }
    public LocalTime getStartsAt() { return startsAt; }
    public void setStartsAt(LocalTime value) { startsAt = value; }
    public LocalTime getEndsAt() { return endsAt; }
    public void setEndsAt(LocalTime value) { endsAt = value; }
    public String getVenue() { return venue; }
    public void setVenue(String value) { venue = value; }
}
