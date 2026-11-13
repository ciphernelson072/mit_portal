package com.sis.security;

import com.sis.service.CustomUserDetailsService;
import com.sis.service.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private final JwtService jwtService = mock(JwtService.class);
    private final CustomUserDetailsService userDetailsService = mock(CustomUserDetailsService.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, userDetailsService);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void temporaryPasswordSessionCannotCallPortalApis() throws ServletException, IOException {
        configureRestrictedUser();
        MockHttpServletRequest request = request("/api/student/profile");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(403, response.getStatus());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void temporaryPasswordSessionCanReachChangePasswordEndpoint() throws ServletException, IOException {
        configureRestrictedUser();
        MockHttpServletRequest request = request("/api/auth/change-password");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        assertEquals(request, chain.getRequest());
    }

    @Test
    void tokenForDeletedUserExpiresAuthCookiesAndContinuesAsUnauthenticated() throws ServletException, IOException {
        when(jwtService.validateToken("stale-token")).thenReturn(true);
        when(jwtService.getUsername("stale-token")).thenReturn("deleted-admin");
        when(userDetailsService.loadUserByUsername("deleted-admin"))
                .thenThrow(new UsernameNotFoundException("User not found"));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin-dashboard.html");
        request.setRequestURI("/admin-dashboard.html");
        request.setCookies(new Cookie(JwtAuthenticationFilter.ACCESS_TOKEN_COOKIE, "stale-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(request, chain.getRequest());
        assertTrue(response.getHeaders(HttpHeaders.SET_COOKIE).stream()
                .anyMatch(cookie -> cookie.startsWith("access_token=") && cookie.contains("Max-Age=0")));
        assertTrue(response.getHeaders(HttpHeaders.SET_COOKIE).stream()
                .anyMatch(cookie -> cookie.startsWith("refresh_token=")
                        && cookie.contains("Path=/api/auth") && cookie.contains("Max-Age=0")));
    }

    private void configureRestrictedUser() {
        when(jwtService.validateToken("restricted-token")).thenReturn(true);
        when(jwtService.getUsername("restricted-token")).thenReturn("new-student");
        when(jwtService.getCredentialVersion("restricted-token")).thenReturn(0);
        when(userDetailsService.loadUserByUsername("new-student"))
                .thenReturn(User.withUsername("new-student")
                        .password("encoded")
                        .authorities("PASSWORD_CHANGE_REQUIRED")
                        .build());
        when(userDetailsService.getCredentialVersion("new-student")).thenReturn(Optional.of(0));
    }

    private MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRequestURI(path);
        request.setCookies(new Cookie(JwtAuthenticationFilter.ACCESS_TOKEN_COOKIE, "restricted-token"));
        return request;
    }
}
