package com.sis.security;

import com.sis.service.CustomUserDetailsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;

import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.Set;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Central security configuration: authentication (JWT, stateless), password hashing
 * (BCrypt), CORS, and the three application roles - ADMIN, TEACHER, STUDENT
 * ({@link com.sis.entity.Role}).
 * <p>
 * Coarse, path-based role rules live here as defense-in-depth; the authoritative,
 * fine-grained checks are the {@code @PreAuthorize} annotations on each controller
 * (e.g. self-service ownership checks via {@link StudentAccessService} /
 * {@link TeacherAccessService}). If a controller's annotations are ever accidentally
 * loosened or removed, these path rules still stop a STUDENT token from reaching
 * {@code /api/admin/**}.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitFilter rateLimitFilter;
    private final OriginValidationFilter originValidationFilter;

    public SecurityConfig(CustomUserDetailsService userDetailsService, JwtAuthenticationFilter jwtAuthenticationFilter,
                           RateLimitFilter rateLimitFilter,
                           @Value("${cors.allowed-origins}") String[] allowedOrigins) {
        this.userDetailsService = userDetailsService;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.rateLimitFilter = rateLimitFilter;
        this.originValidationFilter = new OriginValidationFilter(Set.copyOf(Arrays.asList(allowedOrigins)));
    }

    @Bean
    public RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.fromHierarchy(
                "ADMIN > TEACHER\n" +
                "ADMIN > STUDENT"
        );
    }

        @Bean
        public static MethodSecurityExpressionHandler methodSecurityExpressionHandler(RoleHierarchy roleHierarchy) {
                DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
                handler.setRoleHierarchy(roleHierarchy);
                return handler;
        }

        private static AuthorizationManager<RequestAuthorizationContext> hasExactAuthority(String authority) {
            return (authentication, context) -> {
                Authentication current = authentication.get();
                return new AuthorizationDecision(current != null && current.isAuthenticated()
                        && current.getAuthorities().stream()
                                .anyMatch(granted -> authority.equals(granted.getAuthority())));
            };
        }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Cookies remain SameSite=Strict. Unsafe API requests that include
                // an Origin header are checked against the configured allow-list.
                .csrf(csrf -> csrf.disable())
                .cors(cors -> {}) // delegates to config.WebConfig's CorsConfigurationSource
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        // A conservative CSP: everything defaults to same-origin only. The one
                        // external dependency the frontend actually has is Chart.js from a CDN
                        // (see index.html) - explicitly allow-listed rather than opened broadly.
                        // unsafe-inline is required for style-src because the pages use inline
                        // style="" attributes throughout; tightening that is real follow-up work
                        // (moving inline styles into the stylesheet), not something to silently
                        // paper over by dropping the header entirely.
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; "
                                        + "script-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net; "
                                        + "style-src 'self' 'unsafe-inline'; "
                                        + "img-src 'self' data:; "
                                        + "font-src 'self'; "
                                        + "connect-src 'self'; "
                                        + "frame-ancestors 'none'; "
                                        + "base-uri 'self'; "
                                        + "form-action 'self'"
                        ))
                        .frameOptions(frame -> frame.deny())
                )
                .authorizeHttpRequests(auth -> auth
                        // Public pages and APIs only. Dashboard shells are intentionally not
                        // globally open, so the server enforces the role gate before the UI
                        // renders. The actual role override model is established by the
                        // role hierarchy: ADMIN can supervise TEACHER and STUDENT access.
                        .requestMatchers(
                                "/api/auth/**",
                                "/api/public/**",
                                "/error",
                                "/api/v1/health",
                                "/api/v1/openapi",
                                "/actuator/health", "/actuator/health/**",
                                "/", "/home.html", "/index.html", "/portal-login.html", "/admissions.html",
                                "/admin-login.html", "/course-catalog.html",
                                "/force-password-change.html",
                                "/terms.html", "/privacy.html", "/support.html",
                                "/reset-password.html", "/verify-email.html",
                                "/unauthorized.html",
                                "/robots.txt", "/sitemap.xml",
                                "/public/**",
                                "/manifest.json", "/service-worker.js",
                                "/css/**", "/js/**", "/icons/**", "/favicon.ico"
                        ).permitAll()
                        // Coarse role gates, kept in sync with (not a replacement for) each
                        // controller's own @PreAuthorize.
                        .requestMatchers("/api/admin/exam-permits/**")
                        .hasAnyAuthority("ADMIN", "PERM_EXAM_PERMIT_MANAGE")
                        .requestMatchers("/api/admin/grades/**")
                        .hasAuthority("ADMIN")
                        .requestMatchers("/api/admin/email/**")
                        .hasAnyAuthority("ADMIN", "PERM_EMAIL_CAMPAIGN_MANAGE")
                        .requestMatchers("/api/admin/fees")
                        .hasAnyAuthority("ADMIN", "PERM_FINANCE_FEE_MANAGE")
                        .requestMatchers("/api/admin/institution/**")
                        .hasAnyAuthority("ADMIN", "PERM_ACADEMIC_OPS_MANAGE", "PERM_REGISTRATION_MANAGE")
                        .requestMatchers("/api/admin/system/**")
                        .hasAnyAuthority("ADMIN", "PERM_PORTAL_SETTINGS_MANAGE")
                        .requestMatchers("/api/admin/**").hasAuthority("ADMIN")
                        .requestMatchers("/api/teacher/**")
                        .hasAnyAuthority("ADMIN", "TEACHER", "PERM_ACADEMIC_OPS_MANAGE")
                        .requestMatchers("/api/student/requests/admin/**")
                        .hasAnyAuthority("ADMIN", "PERM_SERVICE_REQUEST_MANAGE")
                        .requestMatchers("/api/student/requests/**").hasAnyAuthority("ADMIN", "STUDENT", "TEACHER")
                        .requestMatchers("/api/student/**").hasAnyAuthority("ADMIN", "STUDENT")
                        .requestMatchers("/api/files/admin/list", "/api/files/delete")
                        .hasAnyAuthority("ADMIN", "PERM_PORTAL_SETTINGS_MANAGE")
                        .requestMatchers("/api/files/**").hasAnyAuthority("ADMIN", "STUDENT", "TEACHER", "PERM_PORTAL_SETTINGS_MANAGE")
                        .requestMatchers("/account-security.html").hasAnyAuthority(
                                "ADMIN", "TEACHER", "STUDENT", "MFA_ENROLLMENT", "PASSWORD_CHANGE_REQUIRED")
                        .requestMatchers("/dashboard.html").access(hasExactAuthority("STUDENT"))
                        .requestMatchers("/academics.html", "/attendance.html", "/finance.html", "/campus-support.html", "/profile.html")
                        .access(hasExactAuthority("STUDENT"))
                        .requestMatchers(
                                "/announcements.html", "/documents.html", "/hostel.html", "/library.html",
                                "/notifications.html", "/research.html", "/scholarships.html",
                                "/support-tickets.html")
                        .access(hasExactAuthority("STUDENT"))
                        .requestMatchers("/admin-dashboard.html").hasAuthority("ADMIN")
                        .requestMatchers("/teacher-dashboard.html").hasAnyAuthority("ADMIN", "TEACHER")
                        .requestMatchers("/operations.html").hasAnyAuthority("ADMIN", "TEACHER", "STUDENT")
                        .requestMatchers("/file-management.html").hasAnyAuthority("ADMIN", "PERM_PORTAL_SETTINGS_MANAGE")
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                                                .authenticationEntryPoint((request, response, authException) -> {
                                                        if (request.getRequestURI().endsWith(".html")) {
                                                                response.sendRedirect("/unauthorized.html?reason=login");
                                                        } else {
                                                                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication required");
                                                        }
                                                })
                                                .accessDeniedHandler((request, response, accessDeniedException) -> {
                                                        if (request.getRequestURI().endsWith(".html")) {
                                                                response.sendRedirect("/unauthorized.html?reason=role");
                                                        } else {
                                                                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied");
                                                        }
                                                })
                )
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(originValidationFilter, RateLimitFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(HttpSecurity http) throws Exception {
        AuthenticationManagerBuilder authenticationManagerBuilder =
                http.getSharedObject(AuthenticationManagerBuilder.class);
        authenticationManagerBuilder.userDetailsService(userDetailsService)
                .passwordEncoder(passwordEncoder());
        return authenticationManagerBuilder.build();
    }
}
