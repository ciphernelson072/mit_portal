package com.sis.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "course_assessment_components",
        uniqueConstraints = @UniqueConstraint(name = "uk_course_assessment_component_code",
                columnNames = {"course_id", "component_code"}))
public class CourseAssessmentComponent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "row_version", nullable = false)
    private Long rowVersion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(name = "component_code", nullable = false, length = 40)
    private String componentCode;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "component_type", nullable = false, length = 20)
    private AssessmentComponentType componentType = AssessmentComponentType.OTHER;

    @Column(name = "weight_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal weightPercentage;

    @Column(name = "max_score", nullable = false, precision = 7, scale = 2)
    private BigDecimal maxScore;

    @Column(name = "required_component", nullable = false)
    private Boolean required = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Course getCourse() { return course; }
    public void setCourse(Course course) { this.course = course; }
    public String getComponentCode() { return componentCode; }
    public void setComponentCode(String componentCode) { this.componentCode = componentCode; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public AssessmentComponentType getComponentType() { return componentType; }
    public void setComponentType(AssessmentComponentType componentType) { this.componentType = componentType; }
    public BigDecimal getWeightPercentage() { return weightPercentage; }
    public void setWeightPercentage(BigDecimal weightPercentage) { this.weightPercentage = weightPercentage; }
    public BigDecimal getMaxScore() { return maxScore; }
    public void setMaxScore(BigDecimal maxScore) { this.maxScore = maxScore; }
    public Boolean getRequired() { return required; }
    public void setRequired(Boolean required) { this.required = required; }
}
