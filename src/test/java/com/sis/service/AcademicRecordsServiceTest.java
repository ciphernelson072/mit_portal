package com.sis.service;

import com.sis.entity.AcademicYear;
import com.sis.entity.Course;
import com.sis.entity.CurriculumCourse;
import com.sis.entity.Grade;
import com.sis.entity.GradeStatus;
import com.sis.entity.Program;
import com.sis.entity.Student;
import com.sis.entity.StudentLifecycleStatus;
import com.sis.entity.StudentProgram;
import com.sis.repository.CurriculumCourseRepository;
import com.sis.repository.GradeRepository;
import com.sis.repository.ProgramRepository;
import com.sis.repository.StudentProgramRepository;
import com.sis.repository.StudentRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AcademicRecordsServiceTest {
    private final StudentRepository students = mock(StudentRepository.class);
    private final StudentProgramRepository studentPrograms = mock(StudentProgramRepository.class);
    private final CurriculumCourseRepository curriculum = mock(CurriculumCourseRepository.class);
    private final GradeRepository grades = mock(GradeRepository.class);
    private final AcademicClearanceService clearance = mock(AcademicClearanceService.class);
    private final ProgramRepository programs = mock(ProgramRepository.class);
    private final AcademicRecordsService service = new AcademicRecordsService(
            students, studentPrograms, curriculum, grades, clearance, programs);

    @Test
    void graduationEligibilityRequiresCompletedRequirementsAndFinancialClearance() {
        Student student = new Student();
        student.setId(42L);
        Program program = new Program();
        program.setId(7L);
        program.setName("Computer Science");
        AcademicYear year = new AcademicYear();
        year.setId(2026L);
        year.setName("2026/2027");
        StudentProgram enrollment = new StudentProgram();
        enrollment.setStudent(student);
        enrollment.setProgram(program);
        enrollment.setAcademicYear(year);
        enrollment.setStatus(StudentLifecycleStatus.ACTIVE);

        Course course = new Course();
        course.setId(99L);
        course.setCourseCode("CSC101");
        course.setName("Introduction to Computing");
        CurriculumCourse requiredCourse = new CurriculumCourse();
        requiredCourse.setCourse(course);
        requiredCourse.setRequired(true);
        requiredCourse.setSemesterNumber(1);
        Grade approvedGrade = new Grade();
        approvedGrade.setCourse(course);
        approvedGrade.setGradeValue("A");
        approvedGrade.setGpaScore(new BigDecimal("4.00"));
        approvedGrade.setStatus(GradeStatus.APPROVED);

        when(students.findById(42L)).thenReturn(Optional.of(student));
        when(studentPrograms.findByStudentIdAndProgramIdAndAcademicYearId(42L, 7L, 2026L))
                .thenReturn(Optional.of(enrollment));
        when(curriculum.findByProgramIdOrderBySemesterNumberAscCourseCourseCodeAsc(7L))
                .thenReturn(List.of(requiredCourse));
        when(grades.findByStudentIdAndStatus(42L, GradeStatus.APPROVED)).thenReturn(List.of(approvedGrade));
        when(programs.existsActiveInDirectory(7L)).thenReturn(true);
        when(clearance.hasActiveFinancialHold(42L)).thenReturn(false);
        when(clearance.hasOutstandingFees(42L)).thenReturn(false);

        Map<String, Object> audit = service.degreeAudit(42L, 7L, 2026L);

        assertThat(audit.get("requiredCourses")).isEqualTo(1);
        assertThat(audit.get("completedRequiredCourses")).isEqualTo(1);
        assertThat(audit.get("academicRequirementsMet")).isEqualTo(true);
        assertThat(audit.get("financialClearanceGranted")).isEqualTo(true);
        assertThat(audit.get("eligibleForGraduation")).isEqualTo(true);
    }

    @Test
    void graduationEligibilityRejectsAnEmptyCurriculumEvenWhenFinanciallyClear() {
        Student student = new Student();
        student.setId(42L);
        Program program = new Program();
        program.setId(7L);
        AcademicYear year = new AcademicYear();
        year.setId(2026L);
        StudentProgram enrollment = new StudentProgram();
        enrollment.setStudent(student);
        enrollment.setProgram(program);
        enrollment.setAcademicYear(year);
        enrollment.setStatus(StudentLifecycleStatus.ACTIVE);

        when(students.findById(42L)).thenReturn(Optional.of(student));
        when(studentPrograms.findByStudentIdAndProgramIdAndAcademicYearId(42L, 7L, 2026L))
                .thenReturn(Optional.of(enrollment));
        when(curriculum.findByProgramIdOrderBySemesterNumberAscCourseCourseCodeAsc(7L))
                .thenReturn(List.of());
        when(grades.findByStudentIdAndStatus(42L, GradeStatus.APPROVED)).thenReturn(List.of());
        when(programs.existsActiveInDirectory(7L)).thenReturn(true);
        when(clearance.hasActiveFinancialHold(42L)).thenReturn(false);
        when(clearance.hasOutstandingFees(42L)).thenReturn(false);

        Map<String, Object> audit = service.degreeAudit(42L, 7L, 2026L);

        assertThat(audit.get("academicRequirementsMet")).isEqualTo(false);
        assertThat(audit.get("eligibleForGraduation")).isEqualTo(false);
    }

    @Test
    void graduationEligibilityRejectsOutstandingFeesOrFinancialHold() {
        Student student = new Student();
        student.setId(42L);
        Program program = new Program();
        program.setId(7L);
        AcademicYear year = new AcademicYear();
        year.setId(2026L);
        StudentProgram enrollment = new StudentProgram();
        enrollment.setStudent(student);
        enrollment.setProgram(program);
        enrollment.setAcademicYear(year);
        enrollment.setStatus(StudentLifecycleStatus.ACTIVE);
        Course course = new Course();
        course.setId(99L);
        course.setCourseCode("CSC101");
        course.setName("Introduction to Computing");
        CurriculumCourse requiredCourse = new CurriculumCourse();
        requiredCourse.setCourse(course);
        requiredCourse.setRequired(true);
        requiredCourse.setSemesterNumber(1);
        Grade approvedGrade = new Grade();
        approvedGrade.setCourse(course);
        approvedGrade.setGpaScore(new BigDecimal("4.00"));
        approvedGrade.setStatus(GradeStatus.APPROVED);

        when(students.findById(42L)).thenReturn(Optional.of(student));
        when(studentPrograms.findByStudentIdOrderByStartDateDesc(42L)).thenReturn(List.of(enrollment));
        when(curriculum.findByProgramIdOrderBySemesterNumberAscCourseCourseCodeAsc(7L))
                .thenReturn(List.of(requiredCourse));
        when(grades.findByStudentIdAndStatus(42L, GradeStatus.APPROVED)).thenReturn(List.of(approvedGrade));
        when(programs.existsActiveInDirectory(7L)).thenReturn(true);
        when(clearance.hasActiveFinancialHold(42L)).thenReturn(true);
        when(clearance.hasOutstandingFees(42L)).thenReturn(false);

        Map<String, Object> audit = service.degreeAudit(42L);

        assertThat(audit.get("academicRequirementsMet")).isEqualTo(true);
        assertThat(audit.get("activeFinancialHold")).isEqualTo(true);
        assertThat(audit.get("financialClearanceGranted")).isEqualTo(false);
        assertThat(audit.get("eligibleForGraduation")).isEqualTo(false);
    }
}
