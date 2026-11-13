package com.sis.service;

import com.sis.dto.AssessmentMarkRequest;
import com.sis.dto.CourseAssessmentComponentRequest;
import com.sis.entity.*;
import com.sis.repository.*;
import com.sis.entity.Student;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class CourseAssessmentService {

    private final CourseRepository courses;
    private final CourseAssessmentComponentRepository components;
    private final GradeRepository grades;
    private final GradeAssessmentMarkRepository marks;
    private final StudentRepository students;
    private final AuditLogRepository auditLogs;
    private final UserRepository users;
    private final AssessmentCalculationService calculations;
    private final AcademicResultCorrectionService resultCorrections;

    public CourseAssessmentService(CourseRepository courses, CourseAssessmentComponentRepository components,
                                   GradeRepository grades, GradeAssessmentMarkRepository marks,
                                   StudentRepository students, AuditLogRepository auditLogs, UserRepository users,
                                   AssessmentCalculationService calculations,
                                   AcademicResultCorrectionService resultCorrections) {
        this.courses = courses;
        this.components = components;
        this.grades = grades;
        this.marks = marks;
        this.students = students;
        this.auditLogs = auditLogs;
        this.users = users;
        this.calculations = calculations;
        this.resultCorrections = resultCorrections;
    }

    @Transactional(readOnly = true)
    public List<CourseAssessmentComponent> components(Long courseId) {
        if (!courses.existsById(courseId)) throw new IllegalArgumentException("Course not found.");
        return components.findByCourseIdOrderByIdAsc(courseId);
    }

    @Transactional(readOnly = true)
    public List<com.sis.dto.AssessmentMarkDto> marksForGrade(Long gradeId) {
        Grade grade = grades.findById(gradeId)
                .orElseThrow(() -> new IllegalArgumentException("Grade not found."));
        Map<Long, GradeAssessmentMark> savedMarks = marks.findByGradeId(gradeId).stream()
                .collect(Collectors.toMap(mark -> mark.getComponent().getId(), mark -> mark));
        return components.findByCourseIdOrderByIdAsc(grade.getCourse().getId()).stream()
                .map(component -> {
                    GradeAssessmentMark saved = savedMarks.get(component.getId());
                    com.sis.dto.AssessmentMarkDto dto = new com.sis.dto.AssessmentMarkDto();
                    dto.setComponentId(component.getId());
                    dto.setComponentCode(component.getComponentCode());
                    dto.setDisplayName(component.getDisplayName());
                    dto.setComponentType(component.getComponentType());
                    dto.setScore(saved == null ? null : saved.getScore());
                    dto.setMaxScore(component.getMaxScore());
                    dto.setWeightPercentage(component.getWeightPercentage());
                    return dto;
                })
                .toList();
    }

    public Grade findOrCreateDraft(Long studentId, Long courseId, String teacher) {
        List<CourseAssessmentComponent> structure = components.findByCourseIdOrderByIdAsc(courseId);
        if (structure.isEmpty()) {
            throw new IllegalStateException("An administrator must configure the course assessment structure first.");
        }
        Student student = students.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found."));
        Course course = courses.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("Course not found."));
        List<Grade> attempts = grades.findByStudentIdAndCourseIdOrderByAttemptNumberAsc(studentId, courseId);
        if (!attempts.isEmpty()) {
            Grade latest = attempts.get(attempts.size() - 1);
            if (latest.getStatus() != GradeStatus.APPROVED) return latest;
            return latest;
        }
        Grade draft = new Grade(student, course, "PEND", "Assessment marks pending.");
        draft.setAttemptNumber(1);
        draft.setGpaScore(BigDecimal.ZERO);
        draft.setGradingPolicyVersion(UgandanTertiaryGradingPolicy.VERSION);
        draft.setRetakeRequired(false);
        draft.setStatus(GradeStatus.PENDING);
        draft.setSubmittedBy(teacher);
        return grades.save(draft);
    }

    public List<CourseAssessmentComponent> saveComponents(
            Long courseId, List<CourseAssessmentComponentRequest> requests, String admin) {
        Course course = courses.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("Course not found."));
        if (grades.existsByCourseIdAndStatus(courseId, GradeStatus.APPROVED)
                || resultCorrections.hasActiveCorrectionsForCourse(courseId)) {
            throw new IllegalStateException(
                    "Assessment structures cannot be changed after a course result has been approved. Use the correction workflow.");
        }
        if (requests == null || requests.isEmpty()) {
            throw new IllegalArgumentException("At least one assessment component is required.");
        }
        Set<String> codes = new HashSet<>();
        List<AssessmentCalculationService.ComponentMark> validation = new ArrayList<>();
        for (CourseAssessmentComponentRequest request : requests) {
            String code = request.getComponentCode().trim().toUpperCase(Locale.ROOT);
            if (!codes.add(code)) throw new IllegalArgumentException("Duplicate assessment component code: " + code);
            validation.add(new AssessmentCalculationService.ComponentMark(
                    request.getWeightPercentage(), request.getMaxScore(), null));
        }
        calculations.calculateTotal(validation);

        List<CourseAssessmentComponent> existingComponents =
                components.findByCourseIdOrderByIdAsc(courseId);
        boolean hasMarks = existingComponents.stream()
                .anyMatch(component -> marks.existsByComponentId(component.getId()));
        if (hasMarks && !sameAssessmentStructure(existingComponents, requests)) {
            throw new IllegalStateException(
                    "Assessment structures with submitted marks are immutable; use an authorized correction process.");
        }
        if (!hasMarks) {
            for (CourseAssessmentComponent existing : existingComponents) {
                boolean retained = requests.stream().anyMatch(request ->
                        existing.getComponentCode().equalsIgnoreCase(request.getComponentCode().trim()));
                if (!retained) components.delete(existing);
            }
        }

        List<CourseAssessmentComponent> saved = new ArrayList<>();
        for (CourseAssessmentComponentRequest request : requests) {
            String code = request.getComponentCode().trim().toUpperCase(Locale.ROOT);
            CourseAssessmentComponent component = components
                    .findByCourseIdAndComponentCodeIgnoreCase(courseId, code)
                    .orElseGet(CourseAssessmentComponent::new);
            component.setCourse(course);
            component.setComponentCode(code);
            component.setDisplayName(request.getDisplayName().trim());
            component.setComponentType(request.getComponentType());
            component.setWeightPercentage(request.getWeightPercentage());
            component.setMaxScore(request.getMaxScore());
            component.setRequired(true);
            saved.add(components.save(component));
        }
        audit(admin, "COURSE_ASSESSMENT_STRUCTURE_UPDATED",
                "Updated assessment structure for course " + courseId + "; weights total 100 percent.");
        return saved;
    }

    private boolean sameAssessmentStructure(List<CourseAssessmentComponent> existing,
                                            List<CourseAssessmentComponentRequest> requests) {
        if (existing.size() != requests.size()) return false;
        for (CourseAssessmentComponent component : existing) {
            CourseAssessmentComponentRequest request = requests.stream()
                    .filter(item -> component.getComponentCode().equalsIgnoreCase(item.getComponentCode().trim()))
                    .findFirst().orElse(null);
            if (request == null
                    || !component.getDisplayName().equals(request.getDisplayName().trim())
                    || component.getComponentType() != request.getComponentType()
                    || component.getWeightPercentage().compareTo(request.getWeightPercentage()) != 0
                    || component.getMaxScore().compareTo(request.getMaxScore()) != 0) {
                return false;
            }
        }
        return true;
    }

    public AssessmentCalculationService.AssessmentTotal saveMarks(
            Long gradeId, AssessmentMarkRequest request, String teacher) {
        Grade grade = resultCorrections.beginAuthorizedCorrection(gradeId, teacher);
        List<CourseAssessmentComponent> structure =
                components.findByCourseIdOrderByIdAsc(grade.getCourse().getId());
        if (structure.isEmpty()) {
            throw new IllegalStateException("An administrator must configure the course assessment structure first.");
        }
        Map<Long, CourseAssessmentComponent> structureById = structure.stream()
                .collect(Collectors.toMap(CourseAssessmentComponent::getId, component -> component));
        Set<Long> provided = new HashSet<>();
        Map<Long, BigDecimal> submittedMarks = new HashMap<>();
        for (AssessmentMarkRequest.ComponentMark mark : request.getMarks()) {
            if (!provided.add(mark.getComponentId())) {
                throw new IllegalArgumentException("Duplicate mark supplied for assessment component " + mark.getComponentId());
            }
            CourseAssessmentComponent component = structureById.get(mark.getComponentId());
            if (component == null) {
                throw new IllegalArgumentException("Assessment component does not belong to this course.");
            }
            if (mark.getScore() != null && (mark.getScore().signum() < 0
                    || mark.getScore().compareTo(component.getMaxScore()) > 0)) {
                throw new IllegalArgumentException("Assessment mark must be between zero and the component maximum.");
            }
            submittedMarks.put(mark.getComponentId(), mark.getScore());
        }

        for (CourseAssessmentComponent component : structure) {
            if (Boolean.TRUE.equals(component.getRequired()) && !provided.contains(component.getId())) {
                throw new IllegalArgumentException("A component must be explicitly supplied; use a null score for a missing mark.");
            }
        }
        List<AssessmentCalculationService.ComponentMark> calculationInput = new ArrayList<>();
        for (CourseAssessmentComponent component : structure) {
            if (!Boolean.TRUE.equals(component.getRequired()) && !provided.contains(component.getId())) continue;
            BigDecimal score = submittedMarks.get(component.getId());
            GradeAssessmentMark mark = marks.findByGradeIdAndComponentId(gradeId, component.getId())
                    .orElseGet(GradeAssessmentMark::new);
            mark.setGrade(grade);
            mark.setComponent(component);
            mark.setScore(score);
            marks.save(mark);
            calculationInput.add(new AssessmentCalculationService.ComponentMark(
                    component.getWeightPercentage(), component.getMaxScore(), score));
        }
        AssessmentCalculationService.AssessmentTotal total = calculations.calculateTotal(calculationInput);
        grade.setTotalMark(total.weightedMark());
        if (total.complete()) {
            grade.setGradeValue(calculations.gradeFromMark(total.weightedMark()));
            grade.setGpaScore(GradeCalculationService.gradeToGpa(grade.getGradeValue()));
            grade.setGradingPolicyVersion(UgandanTertiaryGradingPolicy.VERSION);
            grade.setRetakeRequired(
                    total.weightedMark().compareTo(UgandanTertiaryGradingPolicy.PASS_MARK) < 0);
        } else {
            grade.setGradeValue("PEND");
            grade.setGpaScore(BigDecimal.ZERO);
            grade.setRetakeRequired(false);
        }
        grade.setStatus(GradeStatus.PENDING);
        if (request.getRemarks() != null) {
            grade.setRemarks(request.getRemarks());
        }
        grade.setApprovedBy(null);
        grade.setApprovedAt(null);
        grade.setAssessmentReviewFingerprint(null);
        grade.setAssessmentReviewedBy(null);
        grade.setAssessmentReviewedAt(null);
        grade.setSubmittedBy(teacher);
        grade.setSubmittedAt(LocalDateTime.now());
        grades.save(grade);
        audit(teacher, "ASSESSMENT_MARKS_SUBMITTED",
                "Assessment marks updated for grade " + gradeId + "; complete=" + total.complete());
        return total;
    }

    public Grade createRetake(Long originalGradeId, String admin) {
        Grade selected = grades.findByIdForUpdate(originalGradeId)
                .orElseThrow(() -> new IllegalArgumentException("Original grade attempt not found."));
        Grade original = selected.getOriginalAttempt() == null ? selected : selected.getOriginalAttempt();
        if (original.getStatus() != GradeStatus.APPROVED || !Boolean.TRUE.equals(original.getRetakeRequired())) {
            throw new IllegalStateException("A retake can only be created for an approved failed attempt.");
        }
        List<Grade> attempts = grades.findByStudentIdAndCourseIdOrderByAttemptNumberAsc(
                original.getStudent().getId(), original.getCourse().getId());
        if (attempts.stream().anyMatch(attempt -> attempt.getOriginalAttempt() != null
                && attempt.getStatus() != GradeStatus.APPROVED)) {
            throw new IllegalStateException("An active retake attempt already exists for this course.");
        }
        int nextAttempt = attempts.stream().map(Grade::getAttemptNumber)
                .filter(Objects::nonNull).max(Integer::compareTo).orElse(1) + 1;
        Grade retake = new Grade(original.getStudent(), original.getCourse(), "PEND",
                "Retake required; original attempt preserved.");
        retake.setAttemptNumber(nextAttempt);
        retake.setOriginalAttempt(original);
        retake.setGpaScore(BigDecimal.ZERO);
        retake.setGradingPolicyVersion(UgandanTertiaryGradingPolicy.VERSION);
        retake.setRetakeRequired(false);
        retake.setStatus(GradeStatus.PENDING);
        retake.setSubmittedBy(admin);
        Grade saved = grades.save(retake);
        audit(admin, "GRADE_RETAKE_REQUIRED",
                "Retake attempt " + nextAttempt + " created for original grade " + original.getId()
                        + "; new grade " + saved.getId() + ".");
        return saved;
    }

    private void audit(String username, String action, String details) {
        Long userId = users.findByUsernameNormalized(username)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + username))
                .getId();
        auditLogs.save(new AuditLog(userId, action, details));
    }
}
