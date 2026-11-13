package com.sis.controller;

import com.sis.dto.AttendanceDto;
import com.sis.entity.Attendance;
import com.sis.entity.Course;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
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

    private Course course(Long id) {
        Course course = new Course();
        course.setId(id);
        return course;
    }
}
