package com.sis.service;

import com.sis.dto.AutomaticCourseAssignmentSummary;
import com.sis.entity.StudentProgram;

public record AdmissionEnrollmentResult(
        StudentProgram enrollment,
        AutomaticCourseAssignmentSummary automaticAssignments) {
}
