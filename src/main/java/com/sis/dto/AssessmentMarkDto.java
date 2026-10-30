package com.sis.dto;

import com.sis.entity.AssessmentComponentType;

import java.math.BigDecimal;

public class AssessmentMarkDto {
    private Long componentId;
    private String componentCode;
    private String displayName;
    private AssessmentComponentType componentType;
    private BigDecimal score;
    private BigDecimal maxScore;
    private BigDecimal weightPercentage;

    public Long getComponentId() { return componentId; }
    public void setComponentId(Long componentId) { this.componentId = componentId; }
    public String getComponentCode() { return componentCode; }
    public void setComponentCode(String componentCode) { this.componentCode = componentCode; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public AssessmentComponentType getComponentType() { return componentType; }
    public void setComponentType(AssessmentComponentType componentType) { this.componentType = componentType; }
    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal score) { this.score = score; }
    public BigDecimal getMaxScore() { return maxScore; }
    public void setMaxScore(BigDecimal maxScore) { this.maxScore = maxScore; }
    public BigDecimal getWeightPercentage() { return weightPercentage; }
    public void setWeightPercentage(BigDecimal weightPercentage) { this.weightPercentage = weightPercentage; }
}
