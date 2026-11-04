package com.sis.controller;

import com.sis.dto.AssessmentMarkRequest;
import com.sis.dto.CourseAssessmentComponentRequest;
import com.sis.dto.GradeCorrectionAuthorizationRequest;
import com.sis.dto.GradeCorrectionDto;
import com.sis.entity.Course;
import com.sis.service.CourseAssessmentService;
import com.sis.service.AcademicResultCorrectionService;
import com.sis.service.SchoolService;
import com.sis.dto.GradeDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.Arrays;

@RestController
@RequestMapping("/api")
public class CourseAssessmentController {

    private final CourseAssessmentService assessments;
    private final SchoolService school;
    private final AcademicResultCorrectionService corrections;

    public CourseAssessmentController(CourseAssessmentService assessments, SchoolService school,
                                      AcademicResultCorrectionService corrections) {
        this.assessments = assessments;
        this.school = school;
        this.corrections = corrections;
    }

    @PostMapping("/admin/grades/{gradeId}/corrections")
    @PreAuthorize("@permissionSecurity.canReviewGradeApprovals()")
    public ResponseEntity<GradeCorrectionDto> authorizeCorrection(
            @PathVariable Long gradeId, @Valid @RequestBody GradeCorrectionAuthorizationRequest request) {
        String admin = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                .body(corrections.authorize(gradeId, request.getReason(), admin));
    }

    @GetMapping("/teacher/corrections")
    @PreAuthorize("hasAuthority('TEACHER') or hasAuthority('ADMIN')")
    public ResponseEntity<List<GradeCorrectionDto>> teacherCorrections() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        boolean administrator = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(authority -> "ADMIN".equals(authority.getAuthority()));
        if (administrator) {
            List<Long> courseIds = school.listCourses().stream()
                    .map(Course::getId)
                    .filter(java.util.Objects::nonNull)
                    .toList();
            return ResponseEntity.ok(corrections.listForCourses(courseIds,
                    Arrays.asList(com.sis.entity.GradeCorrectionStatus.values())));
        }
        var teacher = school.getTeacherByUsername(username)
                .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("Teacher profile not found."));
        List<Long> courseIds = school.getCoursesForTeacher(teacher.getId()).stream()
                .map(com.sis.dto.CourseDto::getId)
                .toList();
        return ResponseEntity.ok(corrections.listForCourses(courseIds,
                Arrays.asList(com.sis.entity.GradeCorrectionStatus.values())));
    }

    @GetMapping("/student/corrections")
    @PreAuthorize("hasAuthority('STUDENT')")
    public ResponseEntity<List<GradeCorrectionDto>> studentCorrections() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(corrections.listForStudent(username,
                Arrays.asList(com.sis.entity.GradeCorrectionStatus.values())));
    }

    @PostMapping("/admin/grades/{gradeId}/retakes")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<GradeDto> createRetake(@PathVariable Long gradeId) {
        String admin = SecurityContextHolder.getContext().getAuthentication().getName();
        var retake = assessments.createRetake(gradeId, admin);
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                .body(school.toGradeDto(retake));
    }

    @PutMapping("/admin/courses/{courseId}/assessment-components")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> configureComponents(@PathVariable Long courseId,
            @Valid @RequestBody List<CourseAssessmentComponentRequest> request) {
        String admin = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(assessments.saveComponents(courseId, request, admin).stream()
                .map(CourseAssessmentController::componentPayload).toList());
    }

    @GetMapping("/teacher/courses/{courseId}/assessment-components")
    @PreAuthorize("hasAuthority('TEACHER') or hasAuthority('ADMIN')")
    public ResponseEntity<?> courseComponents(@PathVariable Long courseId) {
        ensureAssignedTeacher(courseId);
        return ResponseEntity.ok(assessments.components(courseId).stream()
                .map(CourseAssessmentController::componentPayload).toList());
    }

    @PostMapping("/teacher/courses/{courseId}/students/{studentId}/grade")
    @PreAuthorize("hasAuthority('TEACHER') or hasAuthority('ADMIN')")
    public ResponseEntity<GradeDto> createGradeDraft(@PathVariable Long courseId, @PathVariable Long studentId) {
        ensureAssignedTeacher(courseId);
        if (!school.studentEnrolledInCourse(studentId, courseId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Student is not enrolled in this course.");
        }
        String actor = SecurityContextHolder.getContext().getAuthentication().getName();
        var grade = assessments.findOrCreateDraft(studentId, courseId, actor);
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(school.toGradeDto(grade));
    }

    @GetMapping("/teacher/grades/{gradeId}/assessment-marks")
    @PreAuthorize("hasAuthority('TEACHER') or hasAuthority('ADMIN')")
    public ResponseEntity<?> assessmentMarks(@PathVariable Long gradeId) {
        var grade = school.getGradeById(gradeId)
                .orElseThrow(() -> new IllegalArgumentException("Grade not found."));
        ensureAssignedTeacher(grade.getCourse().getId());
        return ResponseEntity.ok(assessments.marksForGrade(gradeId));
    }

    @PutMapping("/teacher/grades/{gradeId}/assessment-marks")
    @PreAuthorize("hasAuthority('TEACHER') or hasAuthority('ADMIN')")
    public ResponseEntity<?> submitAssessmentMarks(@PathVariable Long gradeId,
            @Valid @RequestBody AssessmentMarkRequest request) {
        var grade = school.getGradeById(gradeId)
                .orElseThrow(() -> new IllegalArgumentException("Grade not found."));
        ensureAssignedTeacher(grade.getCourse().getId());
        String actor = SecurityContextHolder.getContext().getAuthentication().getName();
        var total = assessments.saveMarks(gradeId, request, actor);
        return ResponseEntity.ok(Map.of(
                "gradeId", gradeId,
                "status", total.complete() ? "COMPLETE" : "PENDING_MARKS",
                "complete", total.complete(),
                "totalMark", total.weightedMark() == null ? "NOT_FINALIZED" : total.weightedMark()));
    }

    private void ensureAssignedTeacher(Long courseId) {
        String actor = SecurityContextHolder.getContext().getAuthentication().getName();
        Course course = school.getCourseById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("Course not found."));
        if (!SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(authority -> "ADMIN".equals(authority.getAuthority()))
                && !school.teacherOwnsCourse(actor, course)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Teacher is not assigned to this course.");
        }
    }

    private static Map<String, Object> componentPayload(com.sis.entity.CourseAssessmentComponent component) {
        return Map.of(
                "id", component.getId(),
                "componentCode", component.getComponentCode(),
                "displayName", component.getDisplayName(),
                "componentType", component.getComponentType(),
                "weightPercentage", component.getWeightPercentage(),
                "maxScore", component.getMaxScore(),
                "required", Boolean.TRUE.equals(component.getRequired()));
    }
}
