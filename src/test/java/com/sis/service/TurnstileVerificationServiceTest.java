package com.sis.service;

import org.junit.jupiter.api.Test;
import com.sis.exception.HumanVerificationUnavailableException;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.http.HttpMethod.POST;

class TurnstileVerificationServiceTest {
    private static final String SITEVERIFY_URL =
            "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    @Test
    void disabledTurnstileDoesNotRequireTokenOrCallProvider() {
        TurnstileVerificationService service =
                new TurnstileVerificationService(false, "", "", new RestTemplate());

        service.verify(null, "admission_submit");

        assertThat(service.isEnabled()).isFalse();
        assertThat(service.getSiteKey()).isEmpty();
    }

    @Test
    void rejectsMissingTokenWhenEnabled() {
        TurnstileVerificationService service =
                new TurnstileVerificationService(true, "site", "secret", new RestTemplate());

        assertThatThrownBy(() -> service.verify(" ", "admission_submit"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("human verification");
    }

    @Test
    void rejectsTokensLongerThanCloudflaresLimitWithoutCallingProvider() {
        TurnstileVerificationService service =
                new TurnstileVerificationService(true, "site", "secret", new RestTemplate());

        assertThatThrownBy(() -> service.verify("x".repeat(2049), "admission_submit"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("human verification");
    }

    @Test
    void requiresSuccessfulVerificationAndExpectedAction() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo(SITEVERIFY_URL))
                .andExpect(method(POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"secret\":\"server-secret\",\"response\":\"client-token\"}"))
                .andRespond(withSuccess("{\"success\":true,\"action\":\"admission_submit\"}",
                        MediaType.APPLICATION_JSON));
        TurnstileVerificationService service =
                new TurnstileVerificationService(true, "public-site-key", "server-secret", restTemplate);

        service.verify("client-token", "admission_submit");

        assertThat(service.getSiteKey()).isEqualTo("public-site-key");
        server.verify();
    }

    @Test
    void rejectsTokensForDifferentActionsOrFailedChallenges() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo(SITEVERIFY_URL))
                .andRespond(withSuccess("{\"success\":true,\"action\":\"application_status\"}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(SITEVERIFY_URL))
                .andRespond(withSuccess("{\"success\":false,\"action\":\"admission_submit\"}",
                        MediaType.APPLICATION_JSON));
        TurnstileVerificationService service =
                new TurnstileVerificationService(true, "site", "secret", restTemplate);

        assertThatThrownBy(() -> service.verify("token-1", "admission_submit"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("verification failed");
        assertThatThrownBy(() -> service.verify("token-2", "admission_submit"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("verification failed");
        server.verify();
    }

    @Test
    void reportsProviderOutageWithoutAcceptingRequest() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo(SITEVERIFY_URL)).andRespond(withServerError());
        TurnstileVerificationService service =
                new TurnstileVerificationService(true, "site", "secret", restTemplate);

        assertThatThrownBy(() -> service.verify("token", "admission_submit"))
                .isInstanceOf(HumanVerificationUnavailableException.class)
                .hasMessageContaining("temporarily unavailable");
        server.verify();
    }

    @Test
    void enabledTurnstileRequiresBothKeys() {
        TurnstileVerificationService service =
                new TurnstileVerificationService(true, "site", "", new RestTemplate());

        assertThatThrownBy(service::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TURNSTILE_SECRET_KEY");
    }
}
