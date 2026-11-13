package com.sis.dto;

import com.sis.entity.AssessmentComponentType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public class CourseAssessmentComponentRequest {

    @NotBlank
    @Size(max = 40)
    private String componentCode;

    @NotBlank
    @Size(max = 120)
    private String displayName;

    @NotNull
    private AssessmentComponentType componentType;

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal weightPercentage;

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal maxScore;

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
}
