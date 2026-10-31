package com.sis.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sis.dto.FlutterwaveCheckoutDetails;
import com.sis.entity.PaymentAttempt;
import com.sis.entity.PaymentAttemptStatus;
import com.sis.entity.PaymentChannel;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FlutterwavePaymentServiceTest {

    @Test
    void duplicateCompletedWebhookIsIgnoredWithoutAnotherProviderConfirmation() throws Exception {
        PaymentAttemptService attempts = mock(PaymentAttemptService.class);
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer.bindTo(restTemplate).build();
        FlutterwavePaymentService service = configuredService(attempts, restTemplate);
        PaymentAttempt completed = pendingAttempt();
        completed.setStatus(PaymentAttemptStatus.COMPLETED);
        completed.setProviderTransactionId("123456");
        when(attempts.findFlutterwaveAttemptIfPresent("MIT-FLW-test-reference"))
                .thenReturn(java.util.Optional.of(completed));

        service.handleWebhook(new ObjectMapper().readTree("""
                {"event":"charge.completed","data":{"id":123456,"tx_ref":"MIT-FLW-test-reference"}}
                """));

        verify(attempts, never()).confirmFlutterwave(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void createsHostedCheckoutUsingServerAmountAndExpectedUgandaMethods() {
        PaymentAttemptService attempts = mock(PaymentAttemptService.class);
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        FlutterwavePaymentService service = configuredService(attempts, restTemplate);
        FlutterwaveCheckoutDetails details = details();
        when(attempts.createFlutterwaveAttempt(25L, 14L)).thenReturn(details);

        server.expect(requestTo("https://api.flutterwave.com/v3/payments"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-secret"))
                .andExpect(content().json("""
                        {
                          "tx_ref": "MIT-FLW-test-reference",
                          "amount": "35000.00",
                          "currency": "UGX",
                          "redirect_url": "https://portal.example.edu/finance.html?payment=return",
                          "customer": {"email": "student@example.edu", "name": "Student Example"},
                          "payment_options": "card, mobilemoneyuganda",
                          "session_duration": 60
                        }
                        """))
                .andRespond(withSuccess("""
                        {"status":"success","data":{"link":"https://checkout.flutterwave.com/v3/hosted/pay/test-link"}}
                        """, MediaType.APPLICATION_JSON));

        var result = service.createCheckout(25L, 14L);

        assertThat(result.getCheckoutUrl()).isEqualTo("https://checkout.flutterwave.com/v3/hosted/pay/test-link");
        assertThat(result.getAmount()).isEqualByComparingTo("35000.00");
        assertThat(result.getCurrency()).isEqualTo("UGX");
        verify(attempts).recordFlutterwaveCheckout(91L,
                "https://checkout.flutterwave.com/v3/hosted/pay/test-link");
        server.verify();
    }

    @Test
    void verifiesTransactionAgainstReferenceExactAmountAndCurrencyBeforeConfirming() {
        PaymentAttemptService attempts = mock(PaymentAttemptService.class);
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        FlutterwavePaymentService service = configuredService(attempts, restTemplate);
        PaymentAttempt attempt = pendingAttempt();
        when(attempts.findStudentFlutterwaveAttempt("MIT-FLW-test-reference", 14L)).thenReturn(attempt);
        PaymentAttempt completed = pendingAttempt();
        completed.setStatus(PaymentAttemptStatus.COMPLETED);
        when(attempts.confirmFlutterwave(91L, "123456")).thenReturn(completed);

        server.expect(requestTo("https://api.flutterwave.com/v3/transactions/123456/verify"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer test-secret"))
                .andRespond(withSuccess("""
                        {"status":"success","data":{"id":123456,"tx_ref":"MIT-FLW-test-reference","status":"successful","currency":"UGX","amount":35000.00}}
                        """, MediaType.APPLICATION_JSON));

        assertThat(service.verifyForStudent("MIT-FLW-test-reference", 123456L, 14L).getStatus())
                .isEqualTo(PaymentAttemptStatus.COMPLETED);
        verify(attempts).confirmFlutterwave(91L, "123456");
        server.verify();
    }

    @Test
    void refusesToConfirmWhenVerifiedAmountDoesNotMatchTheAttempt() {
        PaymentAttemptService attempts = mock(PaymentAttemptService.class);
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        FlutterwavePaymentService service = configuredService(attempts, restTemplate);
        when(attempts.findStudentFlutterwaveAttempt("MIT-FLW-test-reference", 14L))
                .thenReturn(pendingAttempt());
        server.expect(requestTo("https://api.flutterwave.com/v3/transactions/123456/verify"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"status":"success","data":{"id":123456,"tx_ref":"MIT-FLW-test-reference","status":"successful","currency":"UGX","amount":34999.00}}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.verifyForStudent("MIT-FLW-test-reference", 123456L, 14L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("The Flutterwave transaction could not be verified for this fee.");
        server.verify();
    }

    @Test
    void reusesAnExistingUnexpiredHostedCheckout() {
        PaymentAttemptService attempts = mock(PaymentAttemptService.class);
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer.bindTo(restTemplate).build();
        FlutterwavePaymentService service = configuredService(attempts, restTemplate);
        FlutterwaveCheckoutDetails existing = new FlutterwaveCheckoutDetails(91L, 25L,
                "MIT-FLW-test-reference", new BigDecimal("35000.00"), "student@example.edu",
                "Student Example", null, "https://checkout.flutterwave.com/v3/hosted/pay/test-link",
                LocalDateTime.now().plusMinutes(30), true);
        when(attempts.createFlutterwaveAttempt(25L, 14L)).thenReturn(existing);

        var result = service.createCheckout(25L, 14L);

        assertThat(result.getCheckoutUrl()).isEqualTo(existing.getCheckoutUrl());
        assertThat(result.getExpiresAt()).isEqualTo(existing.getExpiresAt());
    }

    @Test
    void failedChargeDoesNotCloseTheHostedCheckoutRetrySession() {
        PaymentAttemptService attempts = mock(PaymentAttemptService.class);
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        FlutterwavePaymentService service = configuredService(attempts, restTemplate);
        PaymentAttempt pending = pendingAttempt();
        when(attempts.findStudentFlutterwaveAttempt("MIT-FLW-test-reference", 14L)).thenReturn(pending);
        server.expect(requestTo("https://api.flutterwave.com/v3/transactions/123456/verify"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"status":"success","data":{"id":123456,"tx_ref":"MIT-FLW-test-reference","status":"failed","currency":"UGX","amount":35000.00}}
                        """, MediaType.APPLICATION_JSON));

        assertThat(service.verifyForStudent("MIT-FLW-test-reference", 123456L, 14L).getStatus())
                .isEqualTo(PaymentAttemptStatus.PENDING);
        verify(attempts, org.mockito.Mockito.never()).confirmFlutterwave(91L, "123456");
        server.verify();
    }

    private FlutterwavePaymentService configuredService(PaymentAttemptService attempts, RestTemplate restTemplate) {
        FlutterwavePaymentService service = new FlutterwavePaymentService(attempts, restTemplate);
        ReflectionTestUtils.setField(service, "secretKey", "test-secret");
        ReflectionTestUtils.setField(service, "webhookSecretHash", "test-webhook-secret-hash");
        ReflectionTestUtils.setField(service, "frontendUrl", "https://portal.example.edu");
        return service;
    }

    private FlutterwaveCheckoutDetails details() {
        return new FlutterwaveCheckoutDetails(91L, 25L, "MIT-FLW-test-reference",
                new BigDecimal("35000.00"), "student@example.edu", "Student Example", null,
                null, LocalDateTime.now().plusMinutes(60), false);
    }

    private PaymentAttempt pendingAttempt() {
        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setId(91L);
        attempt.setFeeId(25L);
        attempt.setStudentId(14L);
        attempt.setAmount(new BigDecimal("35000.00"));
        attempt.setProviderReference("MIT-FLW-test-reference");
        attempt.setPaymentChannel(PaymentChannel.FLUTTERWAVE);
        attempt.setStatus(PaymentAttemptStatus.PENDING);
        return attempt;
    }
}
