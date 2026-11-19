package com.sis.controller;

import com.sis.dto.AttendanceDto;
import com.sis.dto.AttendanceDisputeDto;
import com.sis.dto.CourseDto;
import com.sis.entity.Attendance;
import com.sis.entity.Course;
import com.sis.entity.Teacher;
import com.sis.service.AttendanceService;
import com.sis.service.SchoolService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AttendanceControllerTest {

    private final AttendanceService attendanceService = mock(AttendanceService.class);
    private final SchoolService schoolService = mock(SchoolService.class);
    private final AttendanceController controller = new AttendanceController();

    AttendanceControllerTest() {
        ReflectionTestUtils.setField(controller, "attendanceService", attendanceService);
        ReflectionTestUtils.setField(controller, "schoolService", schoolService);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void teacherAttendanceListIsLimitedToCoursesTheyTeach() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("teacher", "n/a",
                        AuthorityUtils.createAuthorityList("TEACHER")));
        Course assignedCourse = course(10L);
        Course otherCourse = course(11L);
        Attendance ownCourseRecord = new Attendance(null, assignedCourse, LocalDate.now(), true);
        Attendance otherCourseRecord = new Attendance(null, otherCourse, LocalDate.now(), true);
        AttendanceDto ownCourseDto = new AttendanceDto();
        ownCourseDto.setCourseId(10L);
        when(attendanceService.getStudentAttendance(42L))
                .thenReturn(List.of(ownCourseRecord, otherCourseRecord));
        when(schoolService.teacherOwnsCourse("teacher", assignedCourse)).thenReturn(true);
        when(schoolService.teacherOwnsCourse("teacher", otherCourse)).thenReturn(false);
        when(schoolService.toAttendanceDto(ownCourseRecord)).thenReturn(ownCourseDto);

        var response = controller.getStudentAttendance(42L);

        assertThat(response.getBody()).containsExactly(ownCourseDto);
    }

    @Test
    void teacherDisputeListContainsOnlyAssignedCourses() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("teacher", "n/a",
                        AuthorityUtils.createAuthorityList("TEACHER")));
        Teacher teacher = new Teacher();
        teacher.setId(7L);
        when(schoolService.getTeacherByUsername("teacher")).thenReturn(Optional.of(teacher));
        CourseDto assignedCourse = courseDto(10L);
        CourseDto otherCourse = courseDto(11L);
        when(schoolService.getCoursesForTeacher(7L)).thenReturn(List.of(assignedCourse));
        AttendanceDisputeDto dispute = new AttendanceDisputeDto();
        dispute.setCourseId(10L);
        when(attendanceService.listCourseDisputes("teacher", 10L)).thenReturn(List.of(dispute));

        var response = controller.allDisputes();

        assertThat(response.getBody()).containsExactly(dispute);
        verify(attendanceService, never()).listAllDisputes();
        verify(attendanceService).listCourseDisputes("teacher", assignedCourse.getId());
        verify(attendanceService, never()).listCourseDisputes("teacher", otherCourse.getId());
    }

    @Test
    void administratorDisputeListIncludesAllCourses() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", "n/a",
                        AuthorityUtils.createAuthorityList("ADMIN")));
        AttendanceDisputeDto dispute = new AttendanceDisputeDto();
        when(attendanceService.listAllDisputes()).thenReturn(List.of(dispute));

        var response = controller.allDisputes();

        assertThat(response.getBody()).containsExactly(dispute);
        verify(schoolService, never()).getTeacherByUsername("admin");
    }

    private Course course(Long id) {
        Course course = new Course();
        course.setId(id);
        return course;
    }

    private CourseDto courseDto(Long id) {
        CourseDto course = new CourseDto();
        course.setId(id);
        return course;
    }
}
