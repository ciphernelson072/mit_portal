package com.sis.service;

import com.sis.dto.ProgressionRecommendationDto;
import com.sis.entity.CurriculumCourse;
import com.sis.entity.GradeStatus;
import com.sis.entity.StudentLifecycleStatus;
import com.sis.entity.StudentProgram;
import com.sis.exception.ResourceNotFoundException;
import com.sis.repository.CurriculumCourseRepository;
import com.sis.repository.GradeRepository;
import com.sis.repository.StudentProgramRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class AcademicProgressionService {
    private final StudentProgramRepository studentPrograms;
    private final CurriculumCourseRepository curriculumCourses;
    private final GradeRepository grades;

    public AcademicProgressionService(
            StudentProgramRepository studentPrograms,
            CurriculumCourseRepository curriculumCourses,
            GradeRepository grades) {
        this.studentPrograms = studentPrograms;
        this.curriculumCourses = curriculumCourses;
        this.grades = grades;
    }

    @Transactional(readOnly = true)
    public ProgressionRecommendationDto recommendation(Long studentId, Long enrollmentId) {
        StudentProgram enrollment = studentPrograms.findById(enrollmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Student programme enrollment not found with id: " + enrollmentId));
        if (!enrollment.getStudent().getId().equals(studentId)) {
            throw new IllegalArgumentException("Programme enrollment does not belong to this student.");
        }

        Integer currentSemester = enrollment.getCurrentSemester();
        Integer maximumSemester = curriculumCourses
                .findMaximumSemesterNumberByProgramId(enrollment.getProgram().getId());
        List<String> incomplete = new ArrayList<>();
        if (maximumSemester == null || maximumSemester < 1) {
            return result(enrollment, currentSemester, null, false, false, incomplete,
                    "The programme curriculum has no semester numbers configured.");
        }
        if (currentSemester == null || currentSemester < 1 || currentSemester > maximumSemester) {
            return result(enrollment, currentSemester, null, false, false, incomplete,
                    "The stored semester is outside the configured programme curriculum.");
        }

        List<CurriculumCourse> requiredCourses =
                curriculumCourses.findByProgramIdAndSemesterNumberAndRequiredTrueOrderByCourseCourseCodeAsc(
                        enrollment.getProgram().getId(), currentSemester);
        for (CurriculumCourse curriculumCourse : requiredCourses) {
            Long courseId = curriculumCourse.getCourse().getId();
            if (!grades.existsByStudentIdAndCourseIdAndStatusAndGpaScoreGreaterThanEqual(
                    studentId, courseId, GradeStatus.APPROVED,
                    UgandanTertiaryGradingPolicy.PASS_GRADE_POINT)) {
                incomplete.add(curriculumCourse.getCourse().getCourseCode());
            }
        }

        boolean active = enrollment.getStatus() == StudentLifecycleStatus.ACTIVE
                && enrollment.getStudent().getUser() != null
                && Boolean.TRUE.equals(enrollment.getStudent().getUser().getIsActive());
        boolean finalSemester = currentSemester >= maximumSemester;
        boolean eligible = active && !requiredCourses.isEmpty() && incomplete.isEmpty() && !finalSemester;
        String message = !active
                ? "Only active programme enrollments can be recommended for progression."
                : requiredCourses.isEmpty()
                ? "No required courses are configured for the current semester."
                : !incomplete.isEmpty()
                ? "Required courses remain incomplete; review the listed courses."
                : finalSemester
                ? "All required courses are passed; registrar review is required for programme completion."
                : "All required courses are passed; the registrar may approve progression.";

        return result(enrollment, currentSemester, finalSemester ? null : currentSemester + 1,
                eligible, finalSemester, incomplete, message);
    }

    private ProgressionRecommendationDto result(
            StudentProgram enrollment,
            Integer currentSemester,
            Integer recommendedSemester,
            boolean eligible,
            boolean finalSemester,
            List<String> incomplete,
            String message) {
        return new ProgressionRecommendationDto(
                enrollment.getStudent().getId(),
                enrollment.getId(),
                enrollment.getStudent().getFullName(),
                enrollment.getProgram().getName(),
                enrollment.getAcademicYear().getName(),
                currentSemester,
                recommendedSemester,
                eligible,
                finalSemester,
                incomplete,
                message);
    }
}
