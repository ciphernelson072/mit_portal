package com.sis.service;

import com.sis.dto.CreateCourseSectionRequest;
import com.sis.entity.*;
import com.sis.repository.*;
import com.sis.dto.AutomaticCourseAssignmentSummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
public class RegistrationService {
    private final CourseSectionRepository sections;
    private final CourseRegistrationRepository registrations;
    private final CourseRepository courses;
    private final AcademicTermRepository terms;
    private final StudentRepository students;
    private final StudentProgramRepository studentPrograms;
    private final CurriculumCourseRepository curriculumCourses;
    private final CoursePrerequisiteRepository prerequisites;
    private final GradeRepository grades;
    private final FinancialHoldService financialHolds;
    private final TimetableEntryRepository timetableEntries;

    public RegistrationService(CourseSectionRepository sections, CourseRegistrationRepository registrations,
                               CourseRepository courses, AcademicTermRepository terms,
                               StudentRepository students, StudentProgramRepository studentPrograms,
                               CurriculumCourseRepository curriculumCourses,
                               CoursePrerequisiteRepository prerequisites, GradeRepository grades,
                               FinancialHoldService financialHolds,
                               TimetableEntryRepository timetableEntries) {
        this.sections = sections;
        this.registrations = registrations;
        this.courses = courses;
        this.terms = terms;
        this.students = students;
        this.studentPrograms = studentPrograms;
        this.curriculumCourses = curriculumCourses;
        this.prerequisites = prerequisites;
        this.grades = grades;
        this.financialHolds = financialHolds;
        this.timetableEntries = timetableEntries;
    }

    @Transactional(readOnly = true)
    public List<CourseSection> sections(Long termId) {
        return sections.findByAcademicTermIdAndActiveTrueOrderByCourseCourseCodeAsc(termId);
    }

    @Transactional(readOnly = true)
    public Map<Long, Long> registeredCounts(List<CourseSection> courseSections) {
        if (courseSections.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> sectionIds = courseSections.stream().map(CourseSection::getId).toList();
        return registrations.countRegisteredBySectionIds(sectionIds, RegistrationStatus.REGISTERED).stream()
                .collect(Collectors.toMap(
                        SectionRegistrationCount::getSectionId,
                        SectionRegistrationCount::getRegistrationCount,
                        (first, ignored) -> first));
    }

    @Transactional(readOnly = true)
    public List<CourseSection> studentSections(String username, Long termId) {
        Student student = student(username);
        AcademicTerm term = terms.findById(termId)
                .orElseThrow(() -> new IllegalArgumentException("Academic term not found"));
        return sections.findEligibleByAcademicTermAndStudentId(
                termId, student.getId(), StudentLifecycleStatus.ACTIVE);
    }

    public CourseSection createSection(CreateCourseSectionRequest request) {
        Course course = courses.findById(request.getCourseId())
                .orElseThrow(() -> new IllegalArgumentException("Course not found"));
        AcademicTerm term = terms.findById(request.getAcademicTermId())
                .orElseThrow(() -> new IllegalArgumentException("Academic term not found"));
        CourseSection section = new CourseSection();
        section.setCourse(course);
        section.setAcademicTerm(term);
        section.setSectionCode(request.getSectionCode().trim());
        section.setCapacity(request.getCapacity());
        CourseSection saved = sections.save(section);
        return sections.findByIdWithCourseAndTerm(saved.getId())
                .orElseThrow(() -> new IllegalStateException("Saved course section could not be reloaded"));
    }

    @Transactional(readOnly = true)
    public List<CourseRegistration> studentRegistrations(String username) {
        Student student = student(username);
        return registrations.findByStudentIdAndStatusOrderByRegisteredAtDesc(student.getId(), RegistrationStatus.REGISTERED);
    }

    public AutomaticCourseAssignmentSummary autoAssignRequiredCourses(
            String username, Long termId, Long programId, Integer semesterNumber) {
        Student student = students.findByUserUsernameForUpdate(username)
                .orElseThrow(() -> new IllegalArgumentException("Student profile not found"));
        List<CurriculumCourse> requiredCourses =
                curriculumCourses.findByProgramIdAndSemesterNumberAndRequiredTrueOrderByCourseCourseCodeAsc(
                        programId, semesterNumber);
        if (requiredCourses.isEmpty()) {
            return new AutomaticCourseAssignmentSummary(1, 0, 0, 0, List.of());
        }

        List<CourseSection> eligibleSections = sections.findRequiredEligibleByTermStudentProgramAndSemester(
                termId, student.getId(), programId, semesterNumber, StudentLifecycleStatus.ACTIVE);
        Map<Long, Long> registeredCounts = registeredCounts(eligibleSections);
        List<AutomaticCourseAssignmentSummary.Issue> issues = new ArrayList<>();
        int created = 0;
        int alreadyRegistered = 0;

        for (CurriculumCourse curriculumCourse : requiredCourses) {
            Course course = curriculumCourse.getCourse();
            if (registrations.existsByStudentIdAndSectionAcademicTermIdAndSectionCourseIdAndStatus(
                    student.getId(), termId, course.getId(), RegistrationStatus.REGISTERED)) {
                alreadyRegistered++;
                continue;
            }
            if (registrations.existsByStudentIdAndSectionAcademicTermIdAndSectionCourseIdAndStatusIn(
                    student.getId(), termId, course.getId(), List.of(RegistrationStatus.DROPPED))) {
                issues.add(issue(student, programId, course, "Previously dropped by the student; registrar review is required."));
                continue;
            }

            Teacher teacher = course.getTeacher();
            if (teacher == null || teacher.getUser() == null
                    || !Boolean.TRUE.equals(teacher.getUser().getIsActive())) {
                issues.add(issue(student, programId, course, "No active instructor is assigned."));
                continue;
            }

            List<CourseSection> candidates = eligibleSections.stream()
                    .filter(section -> section.getCourse().getId().equals(course.getId()))
                    .sorted(Comparator
                            .comparingLong((CourseSection section) ->
                                    registeredCounts.getOrDefault(section.getId(), 0L))
                            .thenComparing(CourseSection::getSectionCode))
                    .toList();
            if (candidates.isEmpty()) {
                issues.add(issue(student, programId, course, "No active section is available for this semester."));
                continue;
            }

            boolean resolved = false;
            for (CourseSection candidate : candidates) {
                try {
                    register(username, candidate.getId());
                    created++;
                    resolved = true;
                    break;
                } catch (RegistrationRuleViolation violation) {
                    if (violation.getRule() == RegistrationRuleViolation.Rule.SECTION_FULL) {
                        continue;
                    }
                    if (violation.getRule() == RegistrationRuleViolation.Rule.ALREADY_REGISTERED) {
                        alreadyRegistered++;
                        resolved = true;
                    } else {
                        issues.add(issue(student, programId, course, violation.getMessage()));
                        resolved = true;
                    }
                    break;
                }
            }
            if (!resolved) {
                issues.add(issue(student, programId, course, "All active sections are full."));
            }
        }

        return new AutomaticCourseAssignmentSummary(1, created, alreadyRegistered, issues.size(), issues);
    }

    public CourseRegistration register(String username, Long sectionId) {
        Student student = students.findByUserUsernameForUpdate(username)
                .orElseThrow(() -> new IllegalArgumentException("Student profile not found"));
        CourseSection section = sections.findActiveByIdForUpdate(sectionId)
                .orElseThrow(() -> new IllegalArgumentException("Active course section not found"));
        AcademicTerm term = section.getAcademicTerm();
        if (!Boolean.TRUE.equals(term.getRegistrationOpen())) {
            throw new RegistrationRuleViolation(RegistrationRuleViolation.Rule.REGISTRATION_CLOSED,
                    "Registration is closed for this academic term");
        }
        if (!studentPrograms.existsByStudentIdAndAcademicYearIdAndStatus(
                student.getId(), term.getAcademicYear().getId(), StudentLifecycleStatus.ACTIVE)) {
            throw new RegistrationRuleViolation(RegistrationRuleViolation.Rule.NO_ACTIVE_PROGRAM,
                    "Student has no active program for this academic year");
        }
        if (!studentPrograms.existsActiveProgramCourse(
                student.getId(), term.getAcademicYear().getId(), StudentLifecycleStatus.ACTIVE,
                section.getCourse().getId())) {
            throw new RegistrationRuleViolation(RegistrationRuleViolation.Rule.COURSE_NOT_IN_CURRICULUM,
                    "Course is not part of the student's active program curriculum");
        }
        if (financialHolds.hasActiveHold(student.getId())) {
            throw new RegistrationRuleViolation(RegistrationRuleViolation.Rule.FINANCIAL_HOLD,
                    "Registration is blocked by an active financial hold");
        }
        Teacher teacher = section.getCourse().getTeacher();
        if (teacher == null || teacher.getUser() == null || !Boolean.TRUE.equals(teacher.getUser().getIsActive())) {
            throw new RegistrationRuleViolation(RegistrationRuleViolation.Rule.INSTRUCTOR_UNAVAILABLE,
                    "The course does not have an active instructor assigned");
        }
        if (registrations.existsByStudentIdAndSectionAcademicTermIdAndSectionCourseIdAndStatus(
                student.getId(), term.getId(), section.getCourse().getId(), RegistrationStatus.REGISTERED)) {
            throw new RegistrationRuleViolation(RegistrationRuleViolation.Rule.ALREADY_REGISTERED,
                    "Student is already registered for this course in the term");
        }
        try {
            ensureNoTimetableConflict(student.getId(), term.getId(), section.getCourse().getId());
        } catch (IllegalStateException conflict) {
            throw new RegistrationRuleViolation(RegistrationRuleViolation.Rule.TIMETABLE_CONFLICT,
                    conflict.getMessage());
        }
        prerequisites.findByCourseId(section.getCourse().getId()).forEach(item -> {
            if (!grades.existsByStudentIdAndCourseIdAndStatusAndGpaScoreGreaterThanEqual(
                    student.getId(), item.getPrerequisiteCourse().getId(), GradeStatus.APPROVED,
                    UgandanTertiaryGradingPolicy.PASS_GRADE_POINT)) {
                throw new RegistrationRuleViolation(
                        RegistrationRuleViolation.Rule.PREREQUISITE_NOT_COMPLETED,
                        "Prerequisite not completed: " + item.getPrerequisiteCourse().getCourseCode());
            }
        });
        if (registrations.countBySectionIdAndStatus(sectionId, RegistrationStatus.REGISTERED) >= section.getCapacity()) {
            throw new RegistrationRuleViolation(RegistrationRuleViolation.Rule.SECTION_FULL,
                    "Course section is full");
        }
        CourseRegistration registration = registrations.findByStudentIdAndSectionId(student.getId(), sectionId)
                .orElseGet(CourseRegistration::new);
        registration.setStudent(student);
        registration.setSection(section);
        registration.setStatus(RegistrationStatus.REGISTERED);
        registration.setRegisteredAt(LocalDateTime.now());
        registration.setDroppedAt(null);
        CourseRegistration saved = registrations.save(registration);
        return registrations.findByIdWithSectionCourseAndTerm(saved.getId())
                .orElseThrow(() -> new IllegalStateException("Saved course registration could not be reloaded"));
    }

    private AutomaticCourseAssignmentSummary.Issue issue(
            Student student, Long programId, Course course, String reason) {
        return new AutomaticCourseAssignmentSummary.Issue(
                student.getId(), programId, course.getId(), course.getCourseCode(), reason);
    }

    public CourseRegistration drop(String username, Long registrationId) {
        Student student = students.findByUserUsernameForUpdate(username)
                .orElseThrow(() -> new IllegalArgumentException("Student profile not found"));
        CourseRegistration registration = registrations
                .findByIdAndStudentIdWithSectionCourseAndTerm(registrationId, student.getId())
                .orElseThrow(() -> new IllegalArgumentException("Registration not found"));
        CourseSection section = sections.findByIdForUpdate(registration.getSection().getId())
                .orElseThrow(() -> new IllegalArgumentException("Course section not found"));
        if (!Boolean.TRUE.equals(section.getAcademicTerm().getRegistrationOpen())) {
            throw new IllegalStateException("Course dropping is closed for this academic term");
        }
        if (registration.getStatus() != RegistrationStatus.REGISTERED) {
            throw new IllegalStateException("Only an active registration can be dropped");
        }
        registration.setStatus(RegistrationStatus.DROPPED);
        registration.setDroppedAt(LocalDateTime.now());
        return registrations.save(registration);
    }

    private Student student(String username) {
        return students.findByUserUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Student profile not found"));
    }

    private void ensureNoTimetableConflict(Long studentId, Long termId, Long courseId) {
        List<CourseRegistration> currentRegistrations =
                registrations.findByStudentIdAndSectionAcademicTermIdAndStatus(
                        studentId, termId, RegistrationStatus.REGISTERED);
        List<Long> courseIds = new java.util.ArrayList<>();
        courseIds.add(courseId);
        currentRegistrations.stream()
                .map(registration -> registration.getSection().getCourse().getId())
                .filter(registeredCourseId -> !registeredCourseId.equals(courseId))
                .forEach(courseIds::add);

        List<TimetableEntry> courseSchedules = timetableEntries.findByCourseIdIn(courseIds).stream()
                .filter(entry -> entry.getAcademicTermId() == null
                        || entry.getAcademicTermId().equals(termId))
                .toList();
        List<TimetableEntry> candidateSchedules = courseSchedules.stream()
                .filter(entry -> entry.getCourseId().equals(courseId))
                .toList();
        for (CourseRegistration registration : currentRegistrations) {
            Long registeredCourseId = registration.getSection().getCourse().getId();
            if (registeredCourseId.equals(courseId)) {
                continue;
            }
            boolean conflict = courseSchedules.stream()
                    .filter(entry -> entry.getCourseId().equals(registeredCourseId))
                    .anyMatch(existing -> candidateSchedules.stream()
                            .anyMatch(candidate -> overlaps(existing, candidate)));
            if (conflict) {
                throw new IllegalStateException("Course timetable conflicts with an existing registration");
            }
        }
    }

    private boolean overlaps(TimetableEntry first, TimetableEntry second) {
        return first.getDayOfWeek().trim().equalsIgnoreCase(second.getDayOfWeek().trim())
                && first.getStartsAt().isBefore(second.getEndsAt())
                && second.getStartsAt().isBefore(first.getEndsAt());
    }
}