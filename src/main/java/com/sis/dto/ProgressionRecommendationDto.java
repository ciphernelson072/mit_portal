package com.sis.dto;

import java.util.List;

public record ProgressionRecommendationDto(
        Long studentId,
        Long studentProgramId,
        String studentName,
        String programName,
        String academicYearName,
        Integer currentSemester,
        Integer recommendedSemester,
        boolean eligible,
        boolean finalSemester,
        List<String> incompleteRequiredCourses,
        String message) {

    public ProgressionRecommendationDto {
        incompleteRequiredCourses = List.copyOf(incompleteRequiredCourses);
    }
}
