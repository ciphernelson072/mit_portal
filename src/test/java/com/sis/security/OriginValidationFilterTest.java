package com.sis.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.FilterChain;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OriginValidationFilterTest {

    private final OriginValidationFilter filter =
            new OriginValidationFilter(Set.of("https://portal.example.edu"));

    @Test
    void rejectsUnsafeRequestFromUntrustedOrigin() throws Exception {
        MockHttpServletRequest request = request("POST", "/api/auth/login");
        request.addHeader("Origin", "https://attacker.example");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean();

        filter.doFilter(request, response, chain(chainCalled));

        assertEquals(403, response.getStatus());
        assertFalse(chainCalled.get());
    }

    @Test
    void allowsUnsafeRequestFromConfiguredOrigin() throws Exception {
        MockHttpServletRequest request = request("PATCH", "/api/student/profile");
        request.addHeader("Origin", "https://portal.example.edu");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean();

        filter.doFilter(request, response, chain(chainCalled));

        assertTrue(chainCalled.get());
    }

    @Test
    void leavesNonBrowserApiClientsWithoutOriginHeaderCompatible() throws Exception {
        MockHttpServletRequest request = request("POST", "/api/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean();

        filter.doFilter(request, response, chain(chainCalled));

        assertTrue(chainCalled.get());
    }

    @Test
    void skipsSafeAndNonApiRequests() throws Exception {
        for (MockHttpServletRequest request : new MockHttpServletRequest[]{
                request("GET", "/api/student/profile"),
                request("POST", "/public/contact")
        }) {
            request.addHeader("Origin", "https://attacker.example");
            MockHttpServletResponse response = new MockHttpServletResponse();
            AtomicBoolean chainCalled = new AtomicBoolean();

            filter.doFilter(request, response, chain(chainCalled));

            assertTrue(chainCalled.get());
        }
    }

    private MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod(method);
        request.setRequestURI(uri);
        return request;
    }

    private FilterChain chain(AtomicBoolean chainCalled) {
        return (request, response) -> chainCalled.set(true);
    }
}
