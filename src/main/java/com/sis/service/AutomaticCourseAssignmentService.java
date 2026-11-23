package com.sis.service;

import com.sis.dto.AutomaticCourseAssignmentSummary;
import com.sis.entity.AcademicTerm;
import com.sis.entity.StudentLifecycleStatus;
import com.sis.entity.StudentProgram;
import com.sis.repository.AcademicTermRepository;
import com.sis.repository.StudentProgramRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AutomaticCourseAssignmentService {
    private static final int MAX_ISSUE_SAMPLES = 25;

    private final AcademicTermRepository academicTerms;
    private final StudentProgramRepository studentPrograms;
    private final RegistrationService registrationService;

    public AutomaticCourseAssignmentService(AcademicTermRepository academicTerms,
                                            StudentProgramRepository studentPrograms,
                                            RegistrationService registrationService) {
        this.academicTerms = academicTerms;
        this.studentPrograms = studentPrograms;
        this.registrationService = registrationService;
    }

    public AutomaticCourseAssignmentSummary assignStudentForOpenTerms(
            Long studentId, Long programId, Long academicYearId) {
        StudentProgram enrollment = studentPrograms.findByStudentIdAndProgramIdAndAcademicYearId(
                        studentId, programId, academicYearId)
                .orElseThrow(() -> new IllegalArgumentException("Student programme enrollment not found"));
        if (enrollment.getStatus() != StudentLifecycleStatus.ACTIVE
                || enrollment.getStudent().getUser() == null
                || !Boolean.TRUE.equals(enrollment.getStudent().getUser().getIsActive())) {
            return emptySummary();
        }
        return assign(enrollment, academicTerms
                .findByAcademicYearIdAndRegistrationOpenTrueOrderByStartDateAsc(academicYearId));
    }

    public AutomaticCourseAssignmentSummary assignStudentForOpenTerms(Long studentId) {
        if (studentId == null) {
            throw new IllegalArgumentException("Student is required for automatic assignment");
        }
        List<StudentProgram> enrollments =
                studentPrograms.findActiveForAutoAssignmentByStudentId(
                        studentId, StudentLifecycleStatus.ACTIVE);
        return assign(enrollments, findOpenTerms(enrollments));
    }

    public AutomaticCourseAssignmentSummary assignActiveStudentsForTerm(Long termId) {
        AcademicTerm term = academicTerms.findWithAcademicYearById(termId)
                .orElseThrow(() -> new IllegalArgumentException("Academic term not found"));
        if (!Boolean.TRUE.equals(term.getRegistrationOpen())) {
            return emptySummary();
        }
        List<StudentProgram> enrollments =
                studentPrograms.findActiveForAutoAssignmentByAcademicYearId(
                        term.getAcademicYear().getId(), StudentLifecycleStatus.ACTIVE);
        return assign(enrollments, List.of(term));
    }

    public AutomaticCourseAssignmentSummary assignActiveStudentsForProgramSemester(
            Long programId, Integer semesterNumber) {
        if (programId == null || semesterNumber == null) {
            throw new IllegalArgumentException("Programme and semester are required for automatic assignment");
        }
        List<StudentProgram> enrollments =
                studentPrograms.findActiveForAutoAssignmentByProgramIdAndCurrentSemester(
                        programId, semesterNumber, StudentLifecycleStatus.ACTIVE);
        if (enrollments.isEmpty()) {
            return emptySummary();
        }

        return assign(enrollments, findOpenTerms(enrollments));
    }

    public AutomaticCourseAssignmentSummary assignActiveStudentsForCourse(Long courseId) {
        if (courseId == null) {
            throw new IllegalArgumentException("Course is required for automatic assignment");
        }
        List<StudentProgram> enrollments =
                studentPrograms.findActiveForAutoAssignmentByCourseId(
                        courseId, StudentLifecycleStatus.ACTIVE);
        return assign(enrollments, findOpenTerms(enrollments));
    }

    public AutomaticCourseAssignmentSummary assignActiveStudentsForCourseInTerm(
            Long studentId, Long courseId, Long termId) {
        if (studentId == null || courseId == null || termId == null) {
            throw new IllegalArgumentException("Student, course, and term are required for automatic assignment");
        }
        AcademicTerm term = academicTerms.findWithAcademicYearById(termId)
                .orElseThrow(() -> new IllegalArgumentException("Academic term not found"));
        if (!Boolean.TRUE.equals(term.getRegistrationOpen())) {
            return emptySummary();
        }
        Long academicYearId = term.getAcademicYear().getId();
        Map<Long, StudentProgram> enrollmentsById = new LinkedHashMap<>();
        studentPrograms.findActiveForAutoAssignmentByCourseIdAndAcademicYearId(
                        courseId, academicYearId, StudentLifecycleStatus.ACTIVE)
                .forEach(enrollment -> enrollmentsById.put(enrollment.getId(), enrollment));
        studentPrograms.findActiveForAutoAssignmentByStudentId(
                        studentId, StudentLifecycleStatus.ACTIVE).stream()
                .filter(enrollment -> academicYearId.equals(enrollment.getAcademicYear().getId()))
                .forEach(enrollment -> enrollmentsById.putIfAbsent(enrollment.getId(), enrollment));
        return assign(new ArrayList<>(enrollmentsById.values()), List.of(term));
    }

    public AutomaticCourseAssignmentSummary assignActiveStudentsForTeacher(Long teacherId) {
        if (teacherId == null) {
            throw new IllegalArgumentException("Instructor is required for automatic assignment");
        }
        List<StudentProgram> enrollments =
                studentPrograms.findActiveForAutoAssignmentByTeacherId(
                        teacherId, StudentLifecycleStatus.ACTIVE);
        return assign(enrollments, findOpenTerms(enrollments));
    }

    private List<AcademicTerm> findOpenTerms(List<StudentProgram> enrollments) {
        return enrollments.stream()
                .map(enrollment -> enrollment.getAcademicYear().getId())
                .distinct()
                .flatMap(yearId -> academicTerms
                        .findByAcademicYearIdAndRegistrationOpenTrueOrderByStartDateAsc(yearId).stream())
                .toList();
    }

    private AutomaticCourseAssignmentSummary assign(StudentProgram enrollment, List<AcademicTerm> terms) {
        return assign(List.of(enrollment), terms);
    }

    private AutomaticCourseAssignmentSummary assign(
            List<StudentProgram> enrollments, List<AcademicTerm> terms) {
        SummaryBuilder summary = new SummaryBuilder();
        for (AcademicTerm term : terms) {
            for (StudentProgram enrollment : enrollments) {
                if (!term.getAcademicYear().getId().equals(enrollment.getAcademicYear().getId())) {
                    continue;
                }
                summary.add(registrationService.autoAssignRequiredCourses(
                        enrollment.getStudent().getUser().getUsername(),
                        term.getId(),
                        enrollment.getProgram().getId(),
                        enrollment.getCurrentSemester()));
            }
        }
        return summary.build();
    }

    private AutomaticCourseAssignmentSummary emptySummary() {
        return new AutomaticCourseAssignmentSummary(0, 0, 0, 0, List.of());
    }

    private static final class SummaryBuilder {
        private int studentsProcessed;
        private int registrationsCreated;
        private int alreadyRegistered;
        private int skipped;
        private final List<AutomaticCourseAssignmentSummary.Issue> issues = new ArrayList<>();

        private void add(AutomaticCourseAssignmentSummary result) {
            studentsProcessed += result.studentsProcessed();
            registrationsCreated += result.registrationsCreated();
            alreadyRegistered += result.alreadyRegistered();
            skipped += result.skipped();
            for (AutomaticCourseAssignmentSummary.Issue issue : result.issues()) {
                if (issues.size() < MAX_ISSUE_SAMPLES) {
                    issues.add(issue);
                }
            }
        }

        private AutomaticCourseAssignmentSummary build() {
            return new AutomaticCourseAssignmentSummary(
                    studentsProcessed, registrationsCreated, alreadyRegistered, skipped, issues);
        }
    }
}
