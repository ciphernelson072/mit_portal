package com.sis.service;

import com.sis.entity.*;
import com.sis.repository.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RegistrationServiceTest {

    private final CourseSectionRepository sections = mock(CourseSectionRepository.class);
    private final CourseRegistrationRepository registrations = mock(CourseRegistrationRepository.class);
    private final StudentRepository students = mock(StudentRepository.class);
    private final StudentProgramRepository studentPrograms = mock(StudentProgramRepository.class);
    private final CoursePrerequisiteRepository prerequisites = mock(CoursePrerequisiteRepository.class);
    private final GradeRepository grades = mock(GradeRepository.class);
    private final FinancialHoldService financialHolds = mock(FinancialHoldService.class);
    private final TimetableEntryRepository timetables = mock(TimetableEntryRepository.class);
    private final RegistrationService service = new RegistrationService(
            sections, registrations, mock(CourseRepository.class), mock(AcademicTermRepository.class),
            students, studentPrograms, prerequisites, grades, financialHolds, timetables);

    @Test
    void registrationRequiresPrerequisiteGradePointsAtLeastTwo() {
        Student student = new Student();
        student.setId(42L);
        AcademicYear year = new AcademicYear();
        year.setId(2026L);
        AcademicTerm term = new AcademicTerm();
        term.setId(8L);
        term.setAcademicYear(year);
        term.setRegistrationOpen(true);
        Course prerequisiteCourse = new Course();
        prerequisiteCourse.setId(99L);
        prerequisiteCourse.setCourseCode("CSC100");
        Course targetCourse = new Course();
        targetCourse.setId(100L);
        CourseSection section = new CourseSection();
        section.setId(200L);
        section.setAcademicTerm(term);
        section.setCourse(targetCourse);
        CoursePrerequisite prerequisite = new CoursePrerequisite();
        prerequisite.setCourse(targetCourse);
        prerequisite.setPrerequisiteCourse(prerequisiteCourse);

        when(students.findByUserUsernameForUpdate("student")).thenReturn(Optional.of(student));
        when(sections.findActiveByIdForUpdate(200L)).thenReturn(Optional.of(section));
        when(studentPrograms.existsByStudentIdAndAcademicYearIdAndStatus(
                42L, 2026L, StudentLifecycleStatus.ACTIVE)).thenReturn(true);
        when(studentPrograms.existsActiveProgramCourse(
                42L, 2026L, StudentLifecycleStatus.ACTIVE, 100L)).thenReturn(true);
        when(financialHolds.hasActiveHold(42L)).thenReturn(false);
        when(registrations.findByStudentIdAndSectionAcademicTermIdAndStatus(
                42L, 8L, RegistrationStatus.REGISTERED)).thenReturn(List.of());
        when(timetables.findByCourseIdIn(List.of(100L))).thenReturn(List.of());
        when(prerequisites.findByCourseId(100L)).thenReturn(List.of(prerequisite));
        when(grades.existsByStudentIdAndCourseIdAndStatusAndGpaScoreGreaterThanEqual(
                eq(42L), eq(99L), eq(GradeStatus.APPROVED), any(BigDecimal.class)))
                .thenAnswer(invocation -> ((BigDecimal) invocation.getArgument(3))
                        .compareTo(BigDecimal.ONE) == 0);

        assertThatThrownBy(() -> service.register("student", 200L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Prerequisite not completed");
        verify(grades).existsByStudentIdAndCourseIdAndStatusAndGpaScoreGreaterThanEqual(
                42L, 99L, GradeStatus.APPROVED, UgandanTertiaryGradingPolicy.PASS_GRADE_POINT);
    }
}
