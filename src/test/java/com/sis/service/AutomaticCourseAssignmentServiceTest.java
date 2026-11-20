package com.sis.service;

import com.sis.dto.AutomaticCourseAssignmentSummary;
import com.sis.entity.AcademicTerm;
import com.sis.entity.AcademicYear;
import com.sis.entity.Program;
import com.sis.entity.Role;
import com.sis.entity.Student;
import com.sis.entity.StudentLifecycleStatus;
import com.sis.entity.StudentProgram;
import com.sis.entity.User;
import com.sis.repository.AcademicTermRepository;
import com.sis.repository.StudentProgramRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AutomaticCourseAssignmentServiceTest {
    private final AcademicTermRepository academicTerms = mock(AcademicTermRepository.class);
    private final StudentProgramRepository studentPrograms = mock(StudentProgramRepository.class);
    private final RegistrationService registrationService = mock(RegistrationService.class);
    private final AutomaticCourseAssignmentService service =
            new AutomaticCourseAssignmentService(academicTerms, studentPrograms, registrationService);

    @Test
    void assignsOnlyTheEnrolledProgramSemesterInOpenTerms() {
        StudentProgram enrollment = enrollment(2L, 5L, 3);
        AcademicTerm term = term(11L, 2L, true);
        AutomaticCourseAssignmentSummary expected = summary(2, 1);
        when(studentPrograms.findByStudentIdAndProgramIdAndAcademicYearId(19L, 5L, 2L))
                .thenReturn(Optional.of(enrollment));
        when(academicTerms.findByAcademicYearIdAndRegistrationOpenTrueOrderByStartDateAsc(2L))
                .thenReturn(List.of(term));
        when(registrationService.autoAssignRequiredCourses("student", 11L, 5L, 3)).thenReturn(expected);

        AutomaticCourseAssignmentSummary result = service.assignStudentForOpenTerms(19L, 5L, 2L);

        assertThat(result).isEqualTo(expected);
        verify(registrationService).autoAssignRequiredCourses("student", 11L, 5L, 3);
    }

    @Test
    void skipsInactiveStudentAccountsBeforeLookingUpTerms() {
        StudentProgram enrollment = enrollment(2L, 5L, 1);
        enrollment.getStudent().getUser().setIsActive(false);
        when(studentPrograms.findByStudentIdAndProgramIdAndAcademicYearId(19L, 5L, 2L))
                .thenReturn(Optional.of(enrollment));

        AutomaticCourseAssignmentSummary result = service.assignStudentForOpenTerms(19L, 5L, 2L);

        assertThat(result.studentsProcessed()).isZero();
        verify(academicTerms, never())
                .findByAcademicYearIdAndRegistrationOpenTrueOrderByStartDateAsc(2L);
        verify(registrationService, never()).autoAssignRequiredCourses(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void termOpeningAssignsActiveStudentsOnlyWithinThatAcademicYear() {
        AcademicTerm term = term(11L, 2L, true);
        when(academicTerms.findWithAcademicYearById(11L)).thenReturn(Optional.of(term));
        when(studentPrograms.findActiveForAutoAssignmentByAcademicYearId(2L, StudentLifecycleStatus.ACTIVE))
                .thenReturn(List.of(enrollment(2L, 5L, 4), enrollment(3L, 6L, 1)));
        when(registrationService.autoAssignRequiredCourses("student", 11L, 5L, 4))
                .thenReturn(summary(1, 0));

        AutomaticCourseAssignmentSummary result = service.assignActiveStudentsForTerm(11L);

        assertThat(result.studentsProcessed()).isEqualTo(1);
        verify(registrationService).autoAssignRequiredCourses("student", 11L, 5L, 4);
        verify(registrationService, never()).autoAssignRequiredCourses("student", 11L, 6L, 1);
    }

    private StudentProgram enrollment(Long yearId, Long programId, int semester) {
        User user = new User("student", "encoded", Role.STUDENT);
        user.setIsActive(true);
        Student student = new Student();
        student.setId(19L);
        student.setUser(user);
        AcademicYear year = new AcademicYear();
        year.setId(yearId);
        Program program = new Program();
        program.setId(programId);
        StudentProgram enrollment = new StudentProgram();
        enrollment.setStudent(student);
        enrollment.setProgram(program);
        enrollment.setAcademicYear(year);
        enrollment.setStatus(StudentLifecycleStatus.ACTIVE);
        enrollment.setCurrentSemester(semester);
        return enrollment;
    }

    private AcademicTerm term(Long id, Long yearId, boolean registrationOpen) {
        AcademicYear year = new AcademicYear();
        year.setId(yearId);
        AcademicTerm term = new AcademicTerm();
        term.setId(id);
        term.setAcademicYear(year);
        term.setRegistrationOpen(registrationOpen);
        return term;
    }

    private AutomaticCourseAssignmentSummary summary(int created, int skipped) {
        return new AutomaticCourseAssignmentSummary(1, created, 0, skipped, List.of());
    }
}
