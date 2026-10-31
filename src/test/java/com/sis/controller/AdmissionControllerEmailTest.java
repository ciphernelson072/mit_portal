package com.sis.controller;

import com.sis.dto.AdmissionEnrollmentCredentialsResponse;
import com.sis.dto.CreateAdmittedStudentRequest;
import com.sis.service.AdmissionService;
import com.sis.service.EmailService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdmissionControllerEmailTest {

    private final AdmissionService admissionService = mock(AdmissionService.class);
    private final EmailService emailService = mock(EmailService.class);
    private final AdmissionController controller =
            new AdmissionController(admissionService, emailService);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void emailsOneTimeCredentialsAndSuppressesTemporaryPasswordFromAdminResponse() {
        authenticateAsRegistrar();
        AdmissionEnrollmentCredentialsResponse credentials = credentials();
        when(admissionService.createStudentAndEnroll(eq(12L), any(), eq("registrar")))
                .thenReturn(credentials);
        when(emailService.isDeliveryConfigured()).thenReturn(true);

        var response = controller.createStudentAndEnroll(12L, request());

        verify(emailService).sendAdmissionCredentialsEmail(
                "applicant@example.test", "Applicant Name", "applicant", "temporary");
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isEmailSent()).isTrue();
        assertThat(response.getBody().getEmailDeliveryMessage()).contains("One-time sign-in details sent");
        assertThat(response.getBody().getTemporaryPassword()).isNull();
    }

    @Test
    void leavesOneTimeAdminHandoffWhenEmailIsNotConfigured() {
        authenticateAsRegistrar();
        AdmissionEnrollmentCredentialsResponse credentials = credentials();
        when(admissionService.createStudentAndEnroll(eq(12L), any(), eq("registrar")))
                .thenReturn(credentials);
        when(emailService.isDeliveryConfigured()).thenReturn(false);

        var response = controller.createStudentAndEnroll(12L, request());

        verify(emailService, never()).sendAdmissionCredentialsEmail(any(), any(), any(), any());
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isEmailSent()).isFalse();
        assertThat(response.getBody().getTemporaryPassword()).isEqualTo("temporary");
    }

    @Test
    void leavesOneTimeAdminHandoffWhenEmailDeliveryFails() {
        authenticateAsRegistrar();
        AdmissionEnrollmentCredentialsResponse credentials = credentials();
        when(admissionService.createStudentAndEnroll(eq(12L), any(), eq("registrar")))
                .thenReturn(credentials);
        when(emailService.isDeliveryConfigured()).thenReturn(true);
        doThrow(new IllegalStateException("SMTP unavailable")).when(emailService)
                .sendAdmissionCredentialsEmail("applicant@example.test", "Applicant Name", "applicant", "temporary");

        var response = controller.createStudentAndEnroll(12L, request());

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isEmailSent()).isFalse();
        assertThat(response.getBody().getEmailDeliveryMessage()).contains("Email delivery failed");
        assertThat(response.getBody().getTemporaryPassword()).isEqualTo("temporary");
    }

    private void authenticateAsRegistrar() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("registrar", "n/a"));
    }

    private CreateAdmittedStudentRequest request() {
        CreateAdmittedStudentRequest request = new CreateAdmittedStudentRequest();
        request.setEmail("applicant@example.test");
        request.setFullName("Applicant Name");
        return request;
    }

    private AdmissionEnrollmentCredentialsResponse credentials() {
        return new AdmissionEnrollmentCredentialsResponse(
                1L, 2L, 3L, 4L, "ACTIVE", "applicant", "temporary");
    }
}
