package com.sis.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import javax.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "grades")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Grade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "student_id")
    private Student student;

    @ManyToOne(optional = false)
    @JoinColumn(name = "course_id")
    private Course course;

    @Column(nullable = false)
    private String gradeValue;

    // Numeric GPA score for CGPA calculations (0.0 - 4.0)
    @Column(nullable = false, precision = 3, scale = 2)
    private BigDecimal gpaScore = BigDecimal.ZERO;

    @Column(length = 1024)
    private String remarks;

    public Grade() {
    }

    public Grade(Student student, Course course, String gradeValue, String remarks) {
        this.student = student;
        this.course = course;
        this.gradeValue = gradeValue;
        this.remarks = remarks;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Student getStudent() {
        return student;
    }

    public void setStudent(Student student) {
        this.student = student;
    }

    public Course getCourse() {
        return course;
    }

    public void setCourse(Course course) {
        this.course = course;
    }

    public String getGradeValue() {
        return gradeValue;
    }

    public void setGradeValue(String gradeValue) {
        this.gradeValue = gradeValue;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    /**
     * Get numeric GPA score for this grade
     * @return GPA score as BigDecimal (0.0 - 4.0)
     */
    public BigDecimal getGpaScore() {
        return gpaScore;
    }

    /**
     * Set numeric GPA score for this grade
     * @param gpaScore GPA value for CGPA calculations
     */
    public void setGpaScore(BigDecimal gpaScore) {
        this.gpaScore = gpaScore;
    }

    public Object getScore() {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }
}
