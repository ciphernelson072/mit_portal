package com.sis;

import com.sis.controller.TeacherController;
import com.sis.dto.CourseDto;
import com.sis.dto.TeacherResultPublicationStatusDto;
import com.sis.dto.TeacherStudentRosterDto;
import com.sis.dto.CourseUnitDto;
import com.sis.entity.Course;
import com.sis.entity.Student;
import com.sis.entity.Teacher;
import com.sis.entity.Grade;
import com.sis.entity.User;
import com.sis.repository.GradeRepository;
import com.sis.service.AcademicReportService;
import com.sis.service.AttendanceService;
import com.sis.service.SchoolService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Optional;
import java.util.Set;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TeacherControllerFetchTest {

    @AfterEach
    void clearSecurityContext() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    void studentProfileAllowsRegisteredStudentsInTeacherCourses() {
        SchoolService schoolService = mock(SchoolService.class);
        Student student = new Student();
        student.setId(17L);
        student.setFullName("Student Name");
        student.setRegistrationNumber("MIT-017");
        Teacher teacher = new Teacher();
        teacher.setId(12L);
        CourseDto course = new CourseDto();
        course.setId(9L);
        TeacherStudentRosterDto rosterEntry =
                new TeacherStudentRosterDto(17L, "Student Name", "MIT-017");
        when(schoolService.getStudentById(17L)).thenReturn(Optional.of(student));
        when(schoolService.getTeacherByUsername("teacher")).thenReturn(Optional.of(teacher));
        when(schoolService.getCoursesForTeacher(12L)).thenReturn(List.of(course));
        when(schoolService.studentEnrolledInCourse(17L, 9L)).thenReturn(true);
        authenticateTeacher();

        TeacherController controller = new TeacherController(
                schoolService, mock(AttendanceService.class), mock(GradeRepository.class),
                mock(AcademicReportService.class));

        assertEquals(rosterEntry, controller.getStudentProfile(17L).getBody());
        verify(schoolService).studentEnrolledInCourse(17L, 9L);
    }

    @Test
    void courseUnitRosterAuthorizationDoesNotTraverseDetachedTeacherRelations() {
        SchoolService schoolService = mock(SchoolService.class);
        Student student = new Student();
        student.setId(17L);
        student.setFullName("Student Name");
        student.setRegistrationNumber("MIT-017");
        when(schoolService.teacherOwnsCourseUnit("teacher", 23L)).thenReturn(true);
        when(schoolService.listStudentsByCourseUnit(23L)).thenReturn(List.of(student));
        authenticateTeacher();

        TeacherController controller = new TeacherController(
                schoolService, mock(AttendanceService.class), mock(GradeRepository.class),
                mock(AcademicReportService.class));

        assertEquals(new TeacherStudentRosterDto(17L, "Student Name", "MIT-017"),
                controller.listStudentsForCourseUnit(23L).getBody().get(0));
        verify(schoolService).teacherOwnsCourseUnit("teacher", 23L);
    }

    @Test
    void courseRosterReturnsOnlyTeachingRosterFieldsToAssignedTeacher() {
        SchoolService schoolService = mock(SchoolService.class);
        Course course = new Course();
        course.setId(9L);
        Student student = new Student();
        student.setId(17L);
        student.setFullName("Student Name");
        student.setRegistrationNumber("MIT-017");
        when(schoolService.getCourseById(9L)).thenReturn(Optional.of(course));
        when(schoolService.teacherOwnsCourse("teacher", course)).thenReturn(true);
        when(schoolService.listStudentsByCourse(9L)).thenReturn(List.of(student));
        authenticateTeacher();

        TeacherController controller = new TeacherController(
                schoolService, mock(AttendanceService.class), mock(GradeRepository.class),
                mock(AcademicReportService.class));

        var response = controller.listStudentsForCourse(9L);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(List.of(new TeacherStudentRosterDto(17L, "Student Name", "MIT-017")),
                response.getBody());
        verify(schoolService).teacherOwnsCourse("teacher", course);
    }

    @Test
    void legacyDirectGradeSubmissionReturnsGoneWithoutCreatingARecord() {
        SchoolService schoolService = mock(SchoolService.class);
        authenticateTeacher();

        TeacherController controller = new TeacherController(
                schoolService, mock(AttendanceService.class), mock(GradeRepository.class),
                mock(AcademicReportService.class));

        var response = controller.postGrade();
        var updateResponse = controller.updateGrade(42L);

        assertEquals(410, response.getStatusCode().value());
        assertEquals("/api/teacher/grades", response.getBody().getPath());
        assertEquals("Direct grade entry is retired. Use the course assessment workflow to submit marks.",
                response.getBody().getMessage());
        assertEquals(410, updateResponse.getStatusCode().value());
        assertEquals("/api/teacher/grades/42", updateResponse.getBody().getPath());
        verifyNoInteractions(schoolService);
    }

    @Test
    void dashboardUsesScopedSummaryCounts() {
        SchoolService schoolService = mock(SchoolService.class);
        AttendanceService attendanceService = mock(AttendanceService.class);
        Teacher teacher = new Teacher();
        CourseDto course = new CourseDto();
        course.setId(9L);
        when(schoolService.getTeacherByUsername("teacher")).thenReturn(Optional.of(teacher));
        when(schoolService.getCoursesForTeacher(teacher.getId())).thenReturn(List.of(course));
        when(schoolService.getCourseUnitsForTeacher(teacher.getId())).thenReturn(List.of(new CourseUnitDto()));
        when(schoolService.countDistinctStudentsTaughtByTeacher(teacher.getId())).thenReturn(4L);
        when(schoolService.countPendingGradesInCourses(List.of(9L))).thenReturn(2L);
        when(schoolService.countStudentsWithoutAttendanceInCourses(
                org.mockito.ArgumentMatchers.eq(List.of(9L)),
                org.mockito.ArgumentMatchers.any())).thenReturn(5L);
        when(schoolService.countUpcomingAssignmentsInCourses(
                org.mockito.ArgumentMatchers.eq(List.of(9L)),
                org.mockito.ArgumentMatchers.any())).thenReturn(3L);
        when(attendanceService.getCourseAttendance(
                org.mockito.ArgumentMatchers.eq(9L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());
        authenticateTeacher();

        TeacherController controller = new TeacherController(
                schoolService, attendanceService, mock(GradeRepository.class), mock(AcademicReportService.class));

        var overview = controller.getDashboardOverview().getBody();
        assertEquals(1L, overview.getAssignedCourseCount());
        assertEquals(1L, overview.getAssignedCourseUnitCount());
        assertEquals(4L, overview.getEnrolledStudentCount());
        assertEquals(2L, overview.getPendingGradeCount());
        assertEquals(3L, overview.getUpcomingAssignmentCount());
        assertEquals(5L, overview.getPendingAttendanceCount());
    }

    @Test
    void publicationStatusIncludesOnlyAssignedCourseCodesAndNoGradeDetails() {
        SchoolService schoolService = mock(SchoolService.class);
        GradeRepository grades = mock(GradeRepository.class);
        AcademicReportService reports = mock(AcademicReportService.class);
        Teacher teacher = new Teacher();
        teacher.setId(12L);
        User teacherUser = new User("teacher", "encoded", com.sis.entity.Role.TEACHER);
        teacherUser.setId(29L);
        teacher.setUser(teacherUser);
        CourseDto firstCourse = new CourseDto();
        firstCourse.setId(9L);
        CourseDto secondCourse = new CourseDto();
        secondCourse.setId(10L);
        Student student = new Student();
        student.setId(17L);
        student.setFullName("Student Name");
        Course first = new Course();
        first.setId(9L);
        first.setCourseCode("CSC101");
        Course second = new Course();
        second.setId(10L);
        second.setCourseCode("CSC102");
        Grade firstGrade = new Grade(student, first, "A", "not returned");
        Grade secondGrade = new Grade(student, second, "B", "not returned");
        when(schoolService.getTeacherByUsername("teacher")).thenReturn(Optional.of(teacher));
        when(schoolService.getCoursesForTeacher(12L)).thenReturn(List.of(firstCourse, secondCourse));
        when(grades.findByCourseIdsWithStudentAndCourse(List.of(9L, 10L)))
                .thenReturn(List.of(firstGrade, secondGrade));
        TeacherResultPublicationStatusDto status = new TeacherResultPublicationStatusDto(
                17L, "Student Name", List.of("CSC101", "CSC102"), "PENDING", "Waiting for review.");
        when(reports.getTeacherPublicationStatus(17L, "Student Name", List.of("CSC101", "CSC102"), 29L))
                .thenReturn(status);
        authenticateTeacher();

        TeacherController controller = new TeacherController(
                schoolService, mock(AttendanceService.class), grades, reports);

        var response = controller.getResultPublicationStatuses();

        assertEquals(200, response.getStatusCode().value());
        assertEquals(List.of(status), response.getBody());
        verify(grades).findByCourseIdsWithStudentAndCourse(List.of(9L, 10L));
        verify(reports).getTeacherPublicationStatus(17L, "Student Name", List.of("CSC101", "CSC102"), 29L);
    }

    private void authenticateTeacher() {
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("teacher", "n/a",
                        Set.of(new SimpleGrantedAuthority("TEACHER"))));
    }
}
