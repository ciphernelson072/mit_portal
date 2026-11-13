package com.sis.entity;

import javax.persistence.*;

@Entity
@Table(name = "course_prerequisites", uniqueConstraints = @UniqueConstraint(name = "uk_prerequisite_course", columnNames = {"course_id", "prerequisite_course_id"}))
public class CoursePrerequisite {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prerequisite_course_id", nullable = false)
    private Course prerequisiteCourse;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Course getCourse() { return course; }
    public void setCourse(Course course) { this.course = course; }
    public Course getPrerequisiteCourse() { return prerequisiteCourse; }
    public void setPrerequisiteCourse(Course prerequisiteCourse) { this.prerequisiteCourse = prerequisiteCourse; }
}