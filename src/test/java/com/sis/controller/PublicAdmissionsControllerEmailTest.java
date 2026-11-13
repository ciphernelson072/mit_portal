package com.sis.controller;

import com.sis.dto.AdmissionApplicationRequest;
import com.sis.entity.AcademicYear;
import com.sis.entity.AdmissionApplication;
import com.sis.entity.AdmissionApplicationStatus;
import com.sis.entity.Program;
import com.sis.repository.AcademicYearRepository;
import com.sis.repository.ProgramRepository;
import com.sis.service.AdmissionService;
import com.sis.service.EmailService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublicAdmissionsControllerEmailTest {

    private final AdmissionService admissionService = mock(AdmissionService.class);
    private final ProgramRepository programs = mock(ProgramRepository.class);
    private final AcademicYearRepository academicYears = mock(AcademicYearRepository.class);
    private final EmailService emailService = mock(EmailService.class);
    private final PublicAdmissionsController controller =
            new PublicAdmissionsController(admissionService, programs, academicYears, emailService);

    @Test
    void sendsReceiptAfterApplicationIsSavedAndReportsEmailSuccess() {
        AdmissionApplication application = application();
        when(admissionService.submit(any(AdmissionApplicationRequest.class))).thenReturn(application);
        when(emailService.isDeliveryConfigured()).thenReturn(true);

        var response = controller.submit(request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).containsEntry("emailSent", true);
        assertThat(response.getBody().get("emailMessage")).asString().contains("confirmation email has been sent");
        verify(emailService).sendApplicationReceivedEmail(
                "applicant@example.test", "Applicant Name", "APP-123", "Computer Science", "2027/2028");
    }

    @Test
    void keepsApplicationSuccessfulWhenEmailDeliveryFails() {
        when(admissionService.submit(any(AdmissionApplicationRequest.class))).thenReturn(application());
        when(emailService.isDeliveryConfigured()).thenReturn(true);
        org.mockito.Mockito.doThrow(new IllegalStateException("provider unavailable"))
                .when(emailService).sendApplicationReceivedEmail(any(), any(), any(), any(), any());

        var response = controller.submit(request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).containsEntry("emailSent", false);
        assertThat(response.getBody().get("emailMessage")).asString().contains("application number shown on this page");
    }

    @Test
    void confirmsApplicationWasSavedWhenEmailIsNotConfigured() {
        when(admissionService.submit(any(AdmissionApplicationRequest.class))).thenReturn(application());
        when(emailService.isDeliveryConfigured()).thenReturn(false);

        var response = controller.submit(request());

        assertThat(response.getBody()).containsEntry("emailSent", false);
        assertThat(response.getBody().get("applicationNumber")).isEqualTo("APP-123");
        assertThat(response.getBody().get("emailMessage")).asString().contains("not configured");
    }

    private AdmissionApplicationRequest request() {
        AdmissionApplicationRequest request = new AdmissionApplicationRequest();
        request.setEmail("applicant@example.test");
        request.setApplicantName("Applicant Name");
        request.setProgramId(3L);
        request.setAcademicYearId(4L);
        return request;
    }

    private AdmissionApplication application() {
        Program program = new Program();
        program.setName("Computer Science");
        AcademicYear year = new AcademicYear();
        year.setName("2027/2028");
        AdmissionApplication application = new AdmissionApplication();
        application.setApplicationNumber("APP-123");
        application.setApplicantName("Applicant Name");
        application.setEmail("applicant@example.test");
        application.setStatus(AdmissionApplicationStatus.SUBMITTED);
        application.setProgram(program);
        application.setAcademicYear(year);
        return application;
    }
}
