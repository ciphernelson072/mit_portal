package com.sis.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Centralized CORS configuration.
 * <p>
 * This replaces the previous, inconsistent per-controller
 * {@code @CrossOrigin(origins = "*")} annotations, which were present on only 4 of the
 * 13 controllers and used a wildcard origin - insecure, and left every other endpoint
 * with no CORS policy declared at all. Allowed origins are externalized to
 * {@code application.properties} (cors.allowed-origins) rather than hardcoded, so each
 * environment (local dev, staging, production) can configure its own frontend origin(s)
 * without a code change.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${cors.allowed-origins}")
    private String[] allowedOrigins;

    @Value("${cors.allowed-methods}")
    private String[] allowedMethods;

    @Value("${cors.allowed-headers}")
    private String allowedHeaders;

    @Value("${cors.allow-credentials}")
    private boolean allowCredentials;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods(allowedMethods)
                .allowedHeaders(allowedHeaders)
                .allowCredentials(allowCredentials)
                .maxAge(3600);
    }
}
