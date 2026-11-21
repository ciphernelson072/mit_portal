package com.sis.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RequestCorrelationFilterTest {
    @Test
    void propagatesSafeSuppliedRequestIdAndReplacesUnsafeInput() throws Exception {
        RequestCorrelationFilter filter = new RequestCorrelationFilter();
        FilterChain safeChain = (request, response) -> {
            assertThat(((MockHttpServletResponse) response).getHeader(
                    RequestCorrelationFilter.REQUEST_ID_HEADER)).isEqualTo("test-request-123");
        };
        MockHttpServletRequest safeRequest = new MockHttpServletRequest("GET", "/api/student/profile");
        safeRequest.addHeader(RequestCorrelationFilter.REQUEST_ID_HEADER, "test-request-123");
        MockHttpServletResponse safeResponse = new MockHttpServletResponse();

        filter.doFilter(safeRequest, safeResponse, safeChain);
        assertThat(safeResponse.getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER))
                .isEqualTo("test-request-123");

        MockHttpServletRequest unsafeRequest = new MockHttpServletRequest("POST", "/api/auth/login");
        unsafeRequest.addHeader(RequestCorrelationFilter.REQUEST_ID_HEADER, "bad id\r\nInjected");
        MockHttpServletResponse unsafeResponse = new MockHttpServletResponse();
        filter.doFilter(unsafeRequest, unsafeResponse, (request, response) -> {});

        assertThat(unsafeResponse.getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER))
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }
}
