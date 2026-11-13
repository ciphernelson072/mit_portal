package com.sis;

import com.sis.service.AssessmentCalculationService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssessmentCalculationServiceTest {

    private final AssessmentCalculationService calculations = new AssessmentCalculationService();

    @Test
    void calculatesCourseSpecificWeightedComponents() {
        var total = calculations.calculateTotal(List.of(
                new AssessmentCalculationService.ComponentMark(
                        new BigDecimal("40"), new BigDecimal("40"), new BigDecimal("32")),
                new AssessmentCalculationService.ComponentMark(
                        new BigDecimal("60"), new BigDecimal("100"), new BigDecimal("75"))));

        assertTrue(total.complete());
        assertEquals(new BigDecimal("77.00"), total.weightedMark());
    }

    @Test
    void missingMarkIsNotTreatedAsZero() {
        var total = calculations.calculateTotal(List.of(
                new AssessmentCalculationService.ComponentMark(
                        new BigDecimal("40"), new BigDecimal("40"), new BigDecimal("32")),
                new AssessmentCalculationService.ComponentMark(
                        new BigDecimal("60"), new BigDecimal("100"), null)));

        assertFalse(total.complete());
        assertNull(total.weightedMark());
    }

    @Test
    void rejectsBadWeightsAndOutOfRangeMarks() {
        assertThrows(IllegalArgumentException.class, () -> calculations.calculateTotal(List.of(
                new AssessmentCalculationService.ComponentMark(
                        new BigDecimal("40"), new BigDecimal("40"), BigDecimal.ONE))));
        assertThrows(IllegalArgumentException.class, () -> calculations.calculateTotal(List.of(
                new AssessmentCalculationService.ComponentMark(
                        new BigDecimal("100"), new BigDecimal("50"), new BigDecimal("51")))));
    }

    @Test
    void appliesFivePointGradeBoundariesIncludingInclusivePassMark() {
        assertEquals("A", calculations.gradeFromMark(new BigDecimal("80.00")));
        assertEquals("B+", calculations.gradeFromMark(new BigDecimal("79.99")));
        assertEquals("B+", calculations.gradeFromMark(new BigDecimal("75.00")));
        assertEquals("D", calculations.gradeFromMark(new BigDecimal("50.00")));
        assertEquals("F", calculations.gradeFromMark(new BigDecimal("49.99")));
        assertThrows(IllegalArgumentException.class,
                () -> calculations.gradeFromMark(new BigDecimal("100.01")));
    }
}
