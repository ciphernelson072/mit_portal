package com.sis.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public class AssessmentMarkRequest {

    @Valid
    @NotNull
    private List<ComponentMark> marks;
    @Size(max = 1024)
    private String remarks;

    public List<ComponentMark> getMarks() { return marks; }
    public void setMarks(List<ComponentMark> marks) { this.marks = marks; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }

    public static class ComponentMark {
        @NotNull
        private Long componentId;
        private BigDecimal score;

        public Long getComponentId() { return componentId; }
        public void setComponentId(Long componentId) { this.componentId = componentId; }
        public BigDecimal getScore() { return score; }
        public void setScore(BigDecimal score) { this.score = score; }
    }
}
