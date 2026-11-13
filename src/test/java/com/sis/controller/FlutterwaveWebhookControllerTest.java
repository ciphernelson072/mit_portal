package com.sis.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sis.service.FlutterwavePaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class FlutterwaveWebhookControllerTest {

    @Test
    void acceptsMatchingFlutterwaveSecretHash() throws Exception {
        FlutterwavePaymentService payments = mock(FlutterwavePaymentService.class);
        FlutterwaveWebhookController controller = new FlutterwaveWebhookController(payments);
        ReflectionTestUtils.setField(controller, "webhookSecretHash", "private-webhook-secret");

        var response = controller.webhook("private-webhook-secret",
                new ObjectMapper().readTree("{\"event\":\"charge.completed\",\"data\":{}}"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(payments).handleWebhook(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsMissingOrMismatchedFlutterwaveSecretHash() throws Exception {
        FlutterwavePaymentService payments = mock(FlutterwavePaymentService.class);
        FlutterwaveWebhookController controller = new FlutterwaveWebhookController(payments);
        ReflectionTestUtils.setField(controller, "webhookSecretHash", "private-webhook-secret");
        var payload = new ObjectMapper().readTree("{\"event\":\"charge.completed\",\"data\":{}}");

        var response = controller.webhook("wrong-secret", payload);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(payments, never()).handleWebhook(org.mockito.ArgumentMatchers.any());
    }
}
