package com.sis.entity;

import javax.persistence.*;

@Entity
@Table(name = "course_sections", uniqueConstraints = @UniqueConstraint(
        name = "uk_section_term_course_code",
        columnNames = {"academic_term_id", "course_id", "section_code"}))
public class CourseSection {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "academic_term_id", nullable = false)
    private AcademicTerm academicTerm;

    @Column(name = "section_code", nullable = false, length = 30)
    private String sectionCode;

    @Column(nullable = false)
    private Integer capacity = 50;

    @Column(nullable = false)
    private Boolean active = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Course getCourse() { return course; }
    public void setCourse(Course course) { this.course = course; }
    public AcademicTerm getAcademicTerm() { return academicTerm; }
    public void setAcademicTerm(AcademicTerm academicTerm) { this.academicTerm = academicTerm; }
    public String getSectionCode() { return sectionCode; }
    public void setSectionCode(String sectionCode) { this.sectionCode = sectionCode; }
    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
}