package com.sis.security;

import com.sis.service.JwtService;
import com.sis.service.CustomUserDetailsService;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.lang.NonNull;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Authenticates requests from either of two sources:
 * <ul>
 *   <li>An HttpOnly {@code access_token} cookie - the primary path, used by the
 *       browser frontend. Not readable by JavaScript at all (that's the point:
 *       an XSS bug elsewhere in the app can't exfiltrate this the way it could a
 *       token sitting in {@code localStorage}).</li>
 *   <li>An {@code Authorization: Bearer <token>} header - kept for non-browser API
 *       clients (scripts, mobile apps, Postman) that manage their own token storage
 *       and aren't exposed to the browser-XSS threat model a cookie protects against.</li>
 * </ul>
 * The cookie is checked first since it's the primary path for the shipped frontend.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String ACCESS_TOKEN_COOKIE = "access_token";
    private static final String REFRESH_TOKEN_COOKIE = "refresh_token";

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    @Value("${app.security.mfa.required-for-privileged:false}")
    private boolean mfaRequiredForPrivileged;

    @Value("${app.security.cookie-secure:false}")
    private boolean cookieSecure;

    @Value("${app.security.cookie-same-site:Strict}")
    private String cookieSameSite = "Strict";

    public JwtAuthenticationFilter(JwtService jwtService, CustomUserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        String token = extractCookieToken(request);
        if (token == null) {
            token = extractHeaderToken(request);
        }

        String username = null;
        if (token != null && jwtService.validateToken(token)) {
            username = jwtService.getUsername(token);
        }

        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                Integer tokenCredentialVersion = jwtService.getCredentialVersion(token);
                boolean credentialsAreCurrent = tokenCredentialVersion != null
                        && userDetailsService.getCredentialVersion(username)
                                .map(tokenCredentialVersion::equals)
                                .orElse(false);
                if (userDetails.isEnabled() && credentialsAreCurrent
                        && jwtService.isMfaEnrollmentToken(token)) {
                    if (!mfaRequiredForPrivileged || !jwtService.isMfaRequired(token)
                            || !isAllowedDuringMfaEnrollment(request, userDetails)) {
                        expireAuthCookies(response);
                        response.sendError(HttpServletResponse.SC_FORBIDDEN,
                                "MFA enrollment access is restricted");
                        return;
                    }
                    List<GrantedAuthority> enrollmentAuthorities = new ArrayList<>();
                    enrollmentAuthorities.add(new SimpleGrantedAuthority("MFA_ENROLLMENT"));
                    userDetails.getAuthorities().stream()
                            .filter(authority -> "PASSWORD_CHANGE_REQUIRED".equals(authority.getAuthority()))
                            .forEach(enrollmentAuthorities::add);
                    UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                            userDetails, null, enrollmentAuthorities);
                    authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                } else if (userDetails.isEnabled() && credentialsAreCurrent
                        && mfaRequiredForPrivileged
                        && (jwtService.isMfaRequired(token)
                                || MfaPolicy.hasPrivilegedAuthority(userDetails.getAuthorities()))
                        && !jwtService.isMfaVerified(token)) {
                    expireAuthCookies(response);
                } else if (userDetails.isEnabled() && credentialsAreCurrent) {
                    UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                } else {
                    expireAuthCookies(response);
                }
            } catch (UsernameNotFoundException ex) {
                expireAuthCookies(response);
            }
        }

        if (isPasswordChangeRestricted() && request.getRequestURI().startsWith("/api/")
                && !isAllowedDuringPasswordChange(request)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Password change required before using the portal");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void expireAuthCookies(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, expiredCookie(ACCESS_TOKEN_COOKIE, "/").toString());
        response.addHeader(HttpHeaders.SET_COOKIE, expiredCookie(REFRESH_TOKEN_COOKIE, "/api/auth").toString());
    }

    private ResponseCookie expiredCookie(String name, String path) {
        return ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path(path)
                .maxAge(0)
                .build();
    }

    private boolean isPasswordChangeRestricted() {
        org.springframework.security.core.Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("PASSWORD_CHANGE_REQUIRED"::equals);
    }

    private boolean isAllowedDuringPasswordChange(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) return false;
        String path = request.getRequestURI();
        return "/api/auth/change-password".equals(path)
                || "/api/auth/logout".equals(path)
                || "/api/auth/login".equals(path)
                || "/api/auth/forgot-password".equals(path)
                || "/api/auth/validate-reset-token".equals(path)
                || "/api/auth/reset-password".equals(path)
                || "/api/auth/verify-email".equals(path)
                || "/api/auth/validate-email-token".equals(path)
                || "/api/auth/resend-password-reset".equals(path)
                || "/api/auth/resend-email-verification".equals(path);
    }

    private boolean isAllowedDuringMfaEnrollment(HttpServletRequest request, UserDetails userDetails) {
        String path = request.getRequestURI();
        if ("GET".equalsIgnoreCase(request.getMethod())) {
            return "/api/auth/mfa/status".equals(path)
                    || "/account-security.html".equals(path)
                    || ("/force-password-change.html".equals(path)
                            && userDetails.getAuthorities().stream()
                                    .anyMatch(authority -> "PASSWORD_CHANGE_REQUIRED"
                                            .equals(authority.getAuthority())))
                    || path.startsWith("/css/")
                    || path.startsWith("/js/")
                    || path.startsWith("/icons/")
                    || "/manifest.json".equals(path)
                    || "/service-worker.js".equals(path)
                    || "/favicon.ico".equals(path);
        }
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        return "/api/auth/mfa/setup".equals(path)
                || "/api/auth/mfa/verify".equals(path)
                || "/api/auth/change-password".equals(path)
                || "/api/auth/logout".equals(path);
    }

    private String extractCookieToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (ACCESS_TOKEN_COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private String extractHeaderToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
