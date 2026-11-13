package com.sis.entity;

import javax.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "grade_assessment_marks",
        uniqueConstraints = @UniqueConstraint(name = "uk_grade_assessment_mark",
                columnNames = {"grade_id", "assessment_component_id"}))
public class GradeAssessmentMark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grade_id", nullable = false)
    private Grade grade;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assessment_component_id", nullable = false)
    private CourseAssessmentComponent component;

    @Column(precision = 7, scale = 2)
    private BigDecimal score;

    @Column(length = 1024)
    private String remarks;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Grade getGrade() { return grade; }
    public void setGrade(Grade grade) { this.grade = grade; }
    public CourseAssessmentComponent getComponent() { return component; }
    public void setComponent(CourseAssessmentComponent component) { this.component = component; }
    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal score) { this.score = score; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
}
