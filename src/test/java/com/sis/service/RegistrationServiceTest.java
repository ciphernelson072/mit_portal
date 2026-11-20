package com.sis.service;

import com.sis.entity.*;
import com.sis.repository.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RegistrationServiceTest {

    private final CourseSectionRepository sections = mock(CourseSectionRepository.class);
    private final CourseRegistrationRepository registrations = mock(CourseRegistrationRepository.class);
    private final StudentRepository students = mock(StudentRepository.class);
    private final StudentProgramRepository studentPrograms = mock(StudentProgramRepository.class);
    private final CurriculumCourseRepository curriculumCourses = mock(CurriculumCourseRepository.class);
    private final CoursePrerequisiteRepository prerequisites = mock(CoursePrerequisiteRepository.class);
    private final GradeRepository grades = mock(GradeRepository.class);
    private final FinancialHoldService financialHolds = mock(FinancialHoldService.class);
    private final TimetableEntryRepository timetables = mock(TimetableEntryRepository.class);
    private final RegistrationService service = new RegistrationService(
            sections, registrations, mock(CourseRepository.class), mock(AcademicTermRepository.class),
            students, studentPrograms, curriculumCourses,
            prerequisites, grades, financialHolds, timetables);

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
        targetCourse.setTeacher(new Teacher(
                new User("registration-test-teacher", "encoded", Role.TEACHER), "Registration Test Teacher"));
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

    @Test
    void automaticAssignmentChoosesTheLeastEnrolledSectionForTheRequestedSemester() {
        Student student = new Student();
        student.setId(42L);
        Course course = courseWithActiveTeacher();
        CurriculumCourse curriculumCourse = new CurriculumCourse();
        curriculumCourse.setCourse(course);
        CourseSection busierSection = section(201L, "A", course);
        CourseSection lessBusySection = section(202L, "B", course);
        SectionRegistrationCount busyCount = count(201L, 6L);
        SectionRegistrationCount lessBusyCount = count(202L, 2L);
        RegistrationService assignmentService = spy(service);
        doReturn(mock(CourseRegistration.class)).when(assignmentService).register("student", 202L);

        when(students.findByUserUsernameForUpdate("student")).thenReturn(Optional.of(student));
        when(curriculumCourses.findByProgramIdAndSemesterNumberAndRequiredTrueOrderByCourseCourseCodeAsc(7L, 3))
                .thenReturn(List.of(curriculumCourse));
        when(sections.findRequiredEligibleByTermStudentProgramAndSemester(
                8L, 42L, 7L, 3, StudentLifecycleStatus.ACTIVE))
                .thenReturn(List.of(busierSection, lessBusySection));
        when(registrations.countRegisteredBySectionIds(List.of(201L, 202L), RegistrationStatus.REGISTERED))
                .thenReturn(List.of(busyCount, lessBusyCount));
        when(registrations.existsByStudentIdAndSectionAcademicTermIdAndSectionCourseIdAndStatus(
                42L, 8L, 50L, RegistrationStatus.REGISTERED)).thenReturn(false);
        when(registrations.existsByStudentIdAndSectionAcademicTermIdAndSectionCourseIdAndStatusIn(
                42L, 8L, 50L, List.of(RegistrationStatus.DROPPED))).thenReturn(false);

        var result = assignmentService.autoAssignRequiredCourses("student", 8L, 7L, 3);

        assertThat(result.registrationsCreated()).isEqualTo(1);
        verify(assignmentService).register("student", 202L);
        verify(curriculumCourses)
                .findByProgramIdAndSemesterNumberAndRequiredTrueOrderByCourseCourseCodeAsc(7L, 3);
    }

    @Test
    void automaticAssignmentReportsCoursesWithoutAnActiveInstructor() {
        Student student = new Student();
        student.setId(42L);
        Course course = new Course();
        course.setId(50L);
        course.setCourseCode("CSC101");
        CurriculumCourse curriculumCourse = new CurriculumCourse();
        curriculumCourse.setCourse(course);
        CourseSection section = section(201L, "A", course);

        when(students.findByUserUsernameForUpdate("student")).thenReturn(Optional.of(student));
        when(curriculumCourses.findByProgramIdAndSemesterNumberAndRequiredTrueOrderByCourseCourseCodeAsc(7L, 2))
                .thenReturn(List.of(curriculumCourse));
        when(sections.findRequiredEligibleByTermStudentProgramAndSemester(
                8L, 42L, 7L, 2, StudentLifecycleStatus.ACTIVE))
                .thenReturn(List.of(section));
        when(registrations.countRegisteredBySectionIds(List.of(201L), RegistrationStatus.REGISTERED))
                .thenReturn(List.of());
        when(registrations.existsByStudentIdAndSectionAcademicTermIdAndSectionCourseIdAndStatus(
                42L, 8L, 50L, RegistrationStatus.REGISTERED)).thenReturn(false);
        when(registrations.existsByStudentIdAndSectionAcademicTermIdAndSectionCourseIdAndStatusIn(
                42L, 8L, 50L, List.of(RegistrationStatus.DROPPED))).thenReturn(false);

        var result = service.autoAssignRequiredCourses("student", 8L, 7L, 2);

        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.issues()).singleElement()
                .satisfies(issue -> assertThat(issue.reason()).contains("No active instructor"));
        verify(registrations, never()).save(any(CourseRegistration.class));
    }

    private Course courseWithActiveTeacher() {
        User user = new User("instructor", "encoded", Role.TEACHER);
        user.setIsActive(true);
        Course course = new Course();
        course.setId(50L);
        course.setCourseCode("CSC101");
        course.setTeacher(new Teacher(user, "Instructor"));
        return course;
    }

    private CourseSection section(Long id, String code, Course course) {
        CourseSection section = new CourseSection();
        section.setId(id);
        section.setSectionCode(code);
        section.setCourse(course);
        return section;
    }

    private SectionRegistrationCount count(Long sectionId, Long registrations) {
        SectionRegistrationCount count = mock(SectionRegistrationCount.class);
        when(count.getSectionId()).thenReturn(sectionId);
        when(count.getRegistrationCount()).thenReturn(registrations);
        return count;
    }
}
