package com.sis.controller;

import com.sis.dto.StudentProfileDto;
import com.sis.dto.UpdateStudentRequest;
import com.sis.service.AcademicReportService;
import com.sis.service.AttendanceService;
import com.sis.service.SchoolService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StudentControllerTest {

    private final SchoolService schoolService = mock(SchoolService.class);
    private final AcademicReportService academicReports = mock(AcademicReportService.class);
    private final StudentController controller =
            new StudentController(schoolService, mock(AttendanceService.class), academicReports);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void updateProfileDoesNotExposeCgpaBeforePublication() {
        authenticateStudent();
        UpdateStudentRequest request = new UpdateStudentRequest();
        StudentProfileDto profile = profileWithCgpa();
        when(schoolService.updateStudentProfileByUsername("student", request))
                .thenReturn(Optional.of(profile));
        when(academicReports.isOfficiallyPublished(42L)).thenReturn(false);

        var response = controller.updateProfile(request);

        assertThat(response.getBody().getCgpa()).isNull();
    }

    @Test
    void updateProfilePreservesCgpaAfterPublication() {
        authenticateStudent();
        UpdateStudentRequest request = new UpdateStudentRequest();
        StudentProfileDto profile = profileWithCgpa();
        when(schoolService.updateStudentProfileByUsername("student", request))
                .thenReturn(Optional.of(profile));
        when(academicReports.isOfficiallyPublished(42L)).thenReturn(true);

        var response = controller.updateProfile(request);

        assertThat(response.getBody().getCgpa()).isEqualByComparingTo("3.50");
    }

    private void authenticateStudent() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("student", "credentials"));
    }

    private StudentProfileDto profileWithCgpa() {
        StudentProfileDto profile = new StudentProfileDto();
        profile.setId(42L);
        profile.setCgpa(new BigDecimal("3.50"));
        return profile;
    }
}
