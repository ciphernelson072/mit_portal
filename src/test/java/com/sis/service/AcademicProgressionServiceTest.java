package com.sis.service;

import com.sis.entity.AcademicYear;
import com.sis.entity.Course;
import com.sis.entity.CurriculumCourse;
import com.sis.entity.GradeStatus;
import com.sis.entity.Program;
import com.sis.entity.Role;
import com.sis.entity.Student;
import com.sis.entity.StudentLifecycleStatus;
import com.sis.entity.StudentProgram;
import com.sis.entity.User;
import com.sis.repository.CurriculumCourseRepository;
import com.sis.repository.GradeRepository;
import com.sis.repository.StudentProgramRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AcademicProgressionServiceTest {
    private final StudentProgramRepository studentPrograms = mock(StudentProgramRepository.class);
    private final CurriculumCourseRepository curriculumCourses = mock(CurriculumCourseRepository.class);
    private final GradeRepository grades = mock(GradeRepository.class);
    private final AcademicProgressionService service =
            new AcademicProgressionService(studentPrograms, curriculumCourses, grades);

    @Test
    void recommendsNextSemesterOnlyWhenAllRequiredCoursesArePassed() {
        StudentProgram enrollment = enrollment();
        CurriculumCourse completedCourse = curriculumCourse(101L, "CSC101");
        CurriculumCourse pendingCourse = curriculumCourse(102L, "CSC102");
        when(studentPrograms.findById(44L)).thenReturn(Optional.of(enrollment));
        when(curriculumCourses.findMaximumSemesterNumberByProgramId(7L)).thenReturn(4);
        when(curriculumCourses.findByProgramIdAndSemesterNumberAndRequiredTrueOrderByCourseCourseCodeAsc(7L, 2))
                .thenReturn(List.of(completedCourse, pendingCourse));
        when(grades.existsByStudentIdAndCourseIdAndStatusAndGpaScoreGreaterThanEqual(
                19L, 101L, GradeStatus.APPROVED, UgandanTertiaryGradingPolicy.PASS_GRADE_POINT))
                .thenReturn(true);
        when(grades.existsByStudentIdAndCourseIdAndStatusAndGpaScoreGreaterThanEqual(
                19L, 102L, GradeStatus.APPROVED, UgandanTertiaryGradingPolicy.PASS_GRADE_POINT))
                .thenReturn(false);

        var result = service.recommendation(19L, 44L);

        assertThat(result.eligible()).isFalse();
        assertThat(result.recommendedSemester()).isEqualTo(3);
        assertThat(result.incompleteRequiredCourses()).containsExactly("CSC102");
    }

    @Test
    void marksEligibleStudentForRegistrarApprovalWithoutChangingEnrollment() {
        StudentProgram enrollment = enrollment();
        when(studentPrograms.findById(44L)).thenReturn(Optional.of(enrollment));
        when(curriculumCourses.findMaximumSemesterNumberByProgramId(7L)).thenReturn(4);
        when(curriculumCourses.findByProgramIdAndSemesterNumberAndRequiredTrueOrderByCourseCourseCodeAsc(7L, 2))
                .thenReturn(List.of(curriculumCourse(101L, "CSC101")));
        when(grades.existsByStudentIdAndCourseIdAndStatusAndGpaScoreGreaterThanEqual(
                19L, 101L, GradeStatus.APPROVED, UgandanTertiaryGradingPolicy.PASS_GRADE_POINT))
                .thenReturn(true);

        var result = service.recommendation(19L, 44L);

        assertThat(result.eligible()).isTrue();
        assertThat(result.recommendedSemester()).isEqualTo(3);
        assertThat(enrollment.getCurrentSemester()).isEqualTo(2);
    }

    private StudentProgram enrollment() {
        Student student = new Student();
        student.setId(19L);
        student.setFullName("Student Name");
        User user = new User("student", "encoded", Role.STUDENT);
        user.setIsActive(true);
        student.setUser(user);
        Program program = new Program();
        program.setId(7L);
        program.setName("Computer Science");
        AcademicYear year = new AcademicYear();
        year.setId(3L);
        year.setName("2026/2027");
        StudentProgram enrollment = new StudentProgram();
        enrollment.setId(44L);
        enrollment.setStudent(student);
        enrollment.setProgram(program);
        enrollment.setAcademicYear(year);
        enrollment.setStatus(StudentLifecycleStatus.ACTIVE);
        enrollment.setCurrentSemester(2);
        return enrollment;
    }

    private CurriculumCourse curriculumCourse(Long id, String courseCode) {
        Course course = new Course();
        course.setId(id);
        course.setCourseCode(courseCode);
        CurriculumCourse curriculumCourse = new CurriculumCourse();
        curriculumCourse.setCourse(course);
        return curriculumCourse;
    }
}
