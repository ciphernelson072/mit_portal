package com.sis.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {
    private static final Logger logger =
            LoggerFactory.getLogger(RequestCorrelationFilter.class);
    public static final String REQUEST_ID_HEADER = "X-Request-ID";

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        String supplied = request.getHeader(REQUEST_ID_HEADER);
        String requestId = isSafeRequestId(supplied)
                ? supplied
                : UUID.randomUUID().toString();
        MDC.put("requestId", requestId);
        MDC.put("method", request.getMethod());
        MDC.put("route", sanitizeRoute(request.getRequestURI()));
        response.setHeader(REQUEST_ID_HEADER, requestId);
        long startedAt = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            logger.info("http_request status={} durationMs={} "
                            + "requestId={} method={} route={}",
                    response.getStatus(), durationMs,
                    requestId, request.getMethod(), sanitizeRoute(request.getRequestURI()));
            MDC.clear();
        }
    }

    private boolean isSafeRequestId(String value) {
        return value != null
                && value.length() <= 128
                && value.matches("[A-Za-z0-9._:-]+");
    }

    private String sanitizeRoute(String uri) {
        if (uri == null) return "/";
        String cleaned = uri.replaceAll("[\\r\\n]", "");
        if (cleaned.length() > 256) return cleaned.substring(0, 256);
        return cleaned;
    }
}
