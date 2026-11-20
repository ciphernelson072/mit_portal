package com.sis.dto;

import java.util.List;

public record AutomaticCourseAssignmentSummary(
        int studentsProcessed,
        int registrationsCreated,
        int alreadyRegistered,
        int skipped,
        List<Issue> issues) {

    public AutomaticCourseAssignmentSummary {
        issues = List.copyOf(issues);
    }

    public record Issue(Long studentId, Long programId, Long courseId, String courseCode, String reason) {
    }
}
