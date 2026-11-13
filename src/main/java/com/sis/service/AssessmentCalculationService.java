package com.sis.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class AssessmentCalculationService {

    public record ComponentMark(BigDecimal weightPercentage, BigDecimal maxScore, BigDecimal score) {
    }

    public record AssessmentTotal(BigDecimal weightedMark, boolean complete) {
    }

    public AssessmentTotal calculateTotal(List<ComponentMark> components) {
        if (components == null || components.isEmpty()) {
            throw new IllegalArgumentException("An approved course assessment structure is required.");
        }
        BigDecimal weightTotal = BigDecimal.ZERO;
        BigDecimal weightedMark = BigDecimal.ZERO;
        boolean complete = true;
        for (ComponentMark component : components) {
            if (component == null || component.weightPercentage() == null
                    || component.maxScore() == null || component.maxScore().signum() <= 0
                    || component.weightPercentage().signum() <= 0) {
                throw new IllegalArgumentException("Assessment weights and maximum marks must be positive.");
            }
            weightTotal = weightTotal.add(component.weightPercentage());
            if (component.score() == null) {
                complete = false;
                continue;
            }
            if (component.score().signum() < 0 || component.score().compareTo(component.maxScore()) > 0) {
                throw new IllegalArgumentException("Assessment mark must be between zero and the component maximum.");
            }
            weightedMark = weightedMark.add(
                    component.score().multiply(component.weightPercentage())
                            .divide(component.maxScore(), 8, RoundingMode.HALF_UP));
        }
        if (weightTotal.compareTo(new BigDecimal("100")) != 0) {
            throw new IllegalArgumentException("Course assessment weights must total exactly 100 percent.");
        }
        return new AssessmentTotal(complete
                ? weightedMark.setScale(2, RoundingMode.HALF_UP) : null, complete);
    }

    public String gradeFromMark(BigDecimal mark) {
        return UgandanTertiaryGradingPolicy.gradeForMark(mark).letterGrade();
    }
}
