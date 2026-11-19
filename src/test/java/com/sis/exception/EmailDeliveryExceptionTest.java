package com.sis.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import static org.assertj.core.api.Assertions.assertThat;

class EmailDeliveryExceptionTest {
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
}
