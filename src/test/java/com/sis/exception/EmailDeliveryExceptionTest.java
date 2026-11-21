package com.sis.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.dao.DataAccessResourceFailureException;

import static org.assertj.core.api.Assertions.assertThat;

class EmailDeliveryExceptionTest {
    @Test
    void databaseFailuresAreRedactedAsServiceUnavailable() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setRequestURI("/api/auth/change-password");

        var response = new GlobalExceptionHandler().handleDataAccessFailure(
                new DataAccessResourceFailureException(
                        "JDBC exception: Table mit_student_portal.u0_0 doesn't exist"),
                new ServletWebRequest(servletRequest));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().getMessage())
                .isEqualTo("The database is temporarily unavailable. Please try again.");
        assertThat(response.getBody().getMessage()).doesNotContain("u0_0");
    }
    @Test
    void returnsServiceUnavailableForEmailProviderFailures() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setRequestURI("/api/admin/email/test");

        var response = new GlobalExceptionHandler().handleEmailDeliveryFailure(
                new EmailDeliveryException("Email provider could not accept the message."),
                new ServletWebRequest(servletRequest));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).contains("Email provider could not accept");
        assertThat(response.getBody().getPath()).isEqualTo("/api/admin/email/test");
    }

    @Test
    void returnsServiceUnavailableForHumanVerificationProviderFailures() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setRequestURI("/api/public/admissions/applications");

        var response = new GlobalExceptionHandler().handleHumanVerificationUnavailable(
                new HumanVerificationUnavailableException(
                        "Human verification is temporarily unavailable. Please try again.",
                        new IllegalStateException("provider timeout")),
                new ServletWebRequest(servletRequest));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).contains("temporarily unavailable");
        assertThat(response.getBody().getPath()).isEqualTo("/api/public/admissions/applications");
    }
}
