package com.sis.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.servlet.ServletException;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RateLimitFilterTest {

    @Test
    void ignoresForwardedAddressFromUntrustedPeer() throws ServletException, IOException {
        RateLimitFilter filter = configuredFilter("");

        MockHttpServletResponse first = send(filter, "203.0.113.8", "198.51.100.1");
        MockHttpServletResponse second = send(filter, "203.0.113.8", "198.51.100.2");

        assertEquals(200, first.getStatus());
        assertEquals(429, second.getStatus(),
                "An untrusted caller must not rotate buckets by changing X-Forwarded-For");
    }

    @Test
    void acceptsForwardedClientAddressOnlyFromConfiguredProxy() throws ServletException, IOException {
        RateLimitFilter filter = configuredFilter("10.0.0.5");

        MockHttpServletResponse first = send(filter, "10.0.0.5", "198.51.100.1");
        MockHttpServletResponse second = send(filter, "10.0.0.5", "198.51.100.2");

        assertEquals(200, first.getStatus());
        assertEquals(200, second.getStatus(),
                "Distinct client addresses received from a trusted proxy use separate buckets");
    }

    @Test
    void resolvesClientFromRightmostUntrustedAddressInForwardedProxyChain() throws ServletException, IOException {
        RateLimitFilter filter = configuredFilter("10.0.0.5,10.0.0.6");

        MockHttpServletResponse first = send(filter, "10.0.0.5", "198.51.100.1, 10.0.0.6");
        MockHttpServletResponse second = send(filter, "10.0.0.5", "198.51.100.2, 10.0.0.6");

        assertEquals(200, first.getStatus());
        assertEquals(200, second.getStatus(),
                "Requests from different clients behind trusted proxies must not share one rate-limit bucket");
    }

    @Test
    void usesPlatformForwardedClientAddressOnlyWhenExplicitlyEnabled() throws ServletException, IOException {
        RateLimitFilter filter = configuredFilter("10.0.0.5");
        ReflectionTestUtils.setField(filter, "trustPlatformForwardedFor", true);

        MockHttpServletResponse first = send(filter, "10.0.0.5", "198.51.100.1");
        MockHttpServletResponse second = send(filter, "10.0.0.5", "198.51.100.2");

        assertEquals(200, first.getStatus());
        assertEquals(200, second.getStatus(),
                "Distinct platform-forwarded client addresses use separate buckets when enabled");
    }

    @Test
    void ignoresPlatformForwardedAddressFromUntrustedPeer() throws ServletException, IOException {
        RateLimitFilter filter = configuredFilter("10.0.0.5");
        ReflectionTestUtils.setField(filter, "trustPlatformForwardedFor", true);

        assertEquals(200, send(filter, "203.0.113.8", "198.51.100.1").getStatus());
        assertEquals(429, send(filter, "203.0.113.8", "198.51.100.2").getStatus(),
                "Forwarded client IP headers must be ignored unless the direct peer is a trusted proxy");
    }

    @Test
    void ignoresMalformedPlatformForwardedAddress() throws ServletException, IOException {
        RateLimitFilter filter = configuredFilter("");
        ReflectionTestUtils.setField(filter, "trustPlatformForwardedFor", true);

        assertEquals(200, send(filter, "10.0.0.5", "198.51.100.1, not-an-ip").getStatus());
        assertEquals(429, send(filter, "10.0.0.5", "198.51.100.2, not-an-ip").getStatus(),
                "A malformed final forwarded value must fall back to the socket peer");
    }

    @Test
    void limitsPublicAdmissionsSubmissionsAndStatusChecksByClient() throws ServletException, IOException {
        RateLimitFilter filter = configuredAdmissionsFilter();

        assertEquals(200, send(filter, "203.0.113.8", null,
                "POST", "/api/public/admissions/applications").getStatus());
        MockHttpServletResponse limited = send(filter, "203.0.113.8", null,
                "POST", "/api/public/admissions/applications/status");

        assertEquals(429, limited.getStatus());
        assertEquals("application/json", limited.getContentType());
    }

    @Test
    void doesNotLimitPublicAdmissionsOptionReadsOrOtherApiCalls() throws ServletException, IOException {
        RateLimitFilter filter = configuredAdmissionsFilter();

        assertEquals(200, send(filter, "203.0.113.8", null,
                "GET", "/api/public/admissions/programs").getStatus());
        assertEquals(200, send(filter, "203.0.113.8", null,
                "GET", "/api/public/admissions/academic-year").getStatus());
        assertEquals(200, send(filter, "203.0.113.8", null,
                "POST", "/api/auth/login").getStatus());
    }

    @Test
    void boundsRateLimitBucketsAndSharesOverflowBucketByLimiter() throws ServletException, IOException {
        RateLimitFilter filter = configuredFilter("");

        for (int i = 0; i < 10_000; i++) {
            assertEquals(200, send(filter, "198.51." + (i / 256) + "." + (i % 256), null).getStatus());
        }

        assertEquals(200, send(filter, "203.0.113.1", null).getStatus());
        assertEquals(429, send(filter, "203.0.113.2", null).getStatus(),
                "Unknown clients after the cap must share the overflow bucket");
        assertEquals(10_000, ((java.util.Map<?, ?>) ReflectionTestUtils.getField(filter, "buckets")).size());
        assertEquals(1, ((java.util.Map<?, ?>) ReflectionTestUtils.getField(filter, "overflowBuckets")).size());
    }

    private RateLimitFilter configuredFilter(String trustedProxyAddresses) {
        RateLimitFilter filter = new RateLimitFilter();
        ReflectionTestUtils.setField(filter, "capacity", 1);
        ReflectionTestUtils.setField(filter, "refillPerMinute", 0);
        ReflectionTestUtils.setField(filter, "trustedProxyAddressConfig", trustedProxyAddresses);
        filter.initializeTrustedProxies();
        return filter;
    }

    private RateLimitFilter configuredAdmissionsFilter() {
        RateLimitFilter filter = configuredFilter("");
        ReflectionTestUtils.setField(filter, "admissionsCapacity", 1);
        ReflectionTestUtils.setField(filter, "admissionsRefillPerMinute", 0);
        return filter;
    }

    private MockHttpServletResponse send(RateLimitFilter filter, String remoteAddress, String forwardedFor)
            throws ServletException, IOException {
        return send(filter, remoteAddress, forwardedFor, "POST", "/api/auth/login");
    }

    private MockHttpServletResponse send(RateLimitFilter filter, String remoteAddress, String forwardedFor,
                                         String method, String path)
            throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr(remoteAddress);
        if (forwardedFor != null) request.addHeader("X-Forwarded-For", forwardedFor);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
