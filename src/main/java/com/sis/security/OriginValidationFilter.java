package com.sis.security;

import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;

/**
 * Rejects browser state changes initiated by origins outside the configured CORS allow-list.
 */
public final class OriginValidationFilter extends OncePerRequestFilter {

    private final Set<String> allowedOrigins;

    public OriginValidationFilter(Set<String> allowedOrigins) {
        this.allowedOrigins = Set.copyOf(allowedOrigins);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String method = request.getMethod();
        return !request.getRequestURI().contains("/api/")
                || "GET".equalsIgnoreCase(method)
                || "HEAD".equalsIgnoreCase(method)
                || "OPTIONS".equalsIgnoreCase(method);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String origin = request.getHeader("Origin");
        if (origin != null && !allowedOrigins.contains(origin)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Request origin is not allowed.");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
