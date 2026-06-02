package com.sis.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import javax.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "courses")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    // Academic semester/level for the course
    @Column
    private String semester;

    // Credit hours for the course - used in CGPA calculations
    @Column(nullable = false, columnDefinition = "INT DEFAULT 3")
    private Integer credits = 3;

    @ManyToOne(optional = false)
    @JoinColumn(name = "teacher_id")
    private Teacher teacher;

    @ManyToMany
    @JoinTable(name = "course_students",
            joinColumns = @JoinColumn(name = "course_id"),
            inverseJoinColumns = @JoinColumn(name = "student_id"))
    @JsonIgnore
    private Set<Student> students = new HashSet<>();

    public Course() {
    }

    public Course(String name, Teacher teacher) {
        this.name = name;
        this.teacher = teacher;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Teacher getTeacher() {
        return teacher;
    }

    public void setTeacher(Teacher teacher) {
        this.teacher = teacher;
    }

    public Set<Student> getStudents() {
        return students;
    }

    public void setStudents(Set<Student> students) {
        this.students = students;
    }

    /**
     * Get the semester/level for this course
     * @return semester name
     */
    public String getSemester() {
        return semester;
    }

    /**
     * Set the semester/level for this course
     * @param semester academic semester
     */
    public void setSemester(String semester) {
        this.semester = semester;
    }

    /**
     * Get credit hours for this course (used in GPA calculations)
     * @return number of credits
     */
    public Integer getCredits() {
        return credits;
    }

    /**
     * Set credit hours for this course
     * @param credits number of credit hours
     */
    public void setCredits(Integer credits) {
        this.credits = credits;
    }
}
