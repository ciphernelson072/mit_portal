package com.sis.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple in-memory token-bucket rate limiter for authentication and public
 * admissions submission/status endpoints. Keyed by client IP.
 * Forwarded addresses are honored only when the direct socket peer is explicitly
 * configured as a trusted proxy, or when the deployment explicitly enables
 * forwarded addresses from its platform proxy.
 * <p>
 * This is intentionally simple (in-memory, per-instance) rather than a distributed
 * solution (Redis-backed, etc.) - correct and effective for a single-instance
 * deployment; a multi-instance deployment behind a load balancer would need a shared
 * store instead, since each instance would otherwise track its own separate bucket.
 * Flagging that explicitly rather than leaving it as a silent limitation.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    @Value("${ratelimit.auth.capacity}")
    private int capacity;

    @Value("${ratelimit.auth.refill-per-minute}")
    private int refillPerMinute;

    @Value("${ratelimit.auth.trusted-proxies:}")
    private String trustedProxyAddressConfig;

    @Value("${ratelimit.auth.trust-forwarded-for:false}")
    private boolean trustPlatformForwardedFor;

    @Value("${ratelimit.admissions.capacity:5}")
    private int admissionsCapacity;

    @Value("${ratelimit.admissions.refill-per-minute:5}")
    private int admissionsRefillPerMinute;

    private Set<String> trustedProxyAddresses = Collections.emptySet();
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    @PostConstruct
    public void initializeTrustedProxies() {
        if (trustedProxyAddressConfig == null || trustedProxyAddressConfig.isBlank()) {
            trustedProxyAddresses = Collections.emptySet();
            return;
        }
        trustedProxyAddresses = Arrays.stream(trustedProxyAddressConfig.split(","))
                .map(String::trim)
                .filter(address -> !address.isBlank())
                .collect(Collectors.toSet());
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        String requestUri = request.getRequestURI();
        boolean authenticationRequest = requestUri.startsWith("/api/auth/");
        boolean admissionsRequest = "POST".equalsIgnoreCase(request.getMethod())
                && ("/api/public/admissions/applications".equals(requestUri)
                || "/api/public/admissions/applications/status".equals(requestUri));
        if (!authenticationRequest && !admissionsRequest) {
            filterChain.doFilter(request, response);
            return;
        }

        String limiter = authenticationRequest ? "auth" : "admissions";
        int requestCapacity = authenticationRequest ? capacity : admissionsCapacity;
        int refillRate = authenticationRequest ? refillPerMinute : admissionsRefillPerMinute;
        String key = limiter + ":" + clientKey(request);
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(requestCapacity));

        if (!bucket.tryConsume(refillRate)) {
            response.setStatus(429); // Too Many Requests
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"status\":429,\"message\":\"Too many requests - please wait a moment and try again.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String clientKey(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        if (!trustedProxyAddresses.contains(remoteAddress)) {
            return remoteAddress;
        }

        if (trustPlatformForwardedFor) {
            String forwardedClientAddress = platformForwardedClientAddress(request);
            return forwardedClientAddress == null ? remoteAddress : forwardedClientAddress;
        }

        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String[] chain = forwardedFor.split(",");
            for (int i = chain.length - 1; i >= 0; i--) {
                String address = chain[i].trim();
                if (!address.isBlank() && !trustedProxyAddresses.contains(address)) {
                    return address;
                }
            }
        }
        return remoteAddress;
    }

    private String platformForwardedClientAddress(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return null;
        }

        String[] chain = forwardedFor.split(",");
        String lastAddress = chain[chain.length - 1].trim();
        return isIpLiteral(lastAddress) ? lastAddress : null;
    }

    private boolean isIpLiteral(String address) {
        if (address.matches("\\d{1,3}(\\.\\d{1,3}){3}")) {
            String[] octets = address.split("\\.");
            for (String octet : octets) {
                int value = Integer.parseInt(octet);
                if (value > 255) {
                    return false;
                }
            }
            return true;
        }
        if (address.indexOf(':') < 0 || !address.matches("(?i)[0-9a-f:]+")) {
            return false;
        }
        try {
            return InetAddress.getByName(address) instanceof Inet6Address;
        } catch (UnknownHostException exception) {
            return false;
        }
    }

    /** A minimal token bucket: refills linearly over time, capped at capacity. */
    private static class Bucket {
        private final AtomicInteger tokens;
        private volatile long lastRefillEpochMs = System.currentTimeMillis();
        private final int capacity;

        Bucket(int capacity) {
            this.capacity = capacity;
            this.tokens = new AtomicInteger(capacity);
        }

        synchronized boolean tryConsume(int refillPerMinute) {
            refill(refillPerMinute);
            if (tokens.get() > 0) {
                tokens.decrementAndGet();
                return true;
            }
            return false;
        }

        private void refill(int refillPerMinute) {
            long now = System.currentTimeMillis();
            long elapsedMs = now - lastRefillEpochMs;
            long toAdd = (elapsedMs * refillPerMinute) / 60_000L;
            if (toAdd > 0) {
                tokens.updateAndGet(t -> Math.min(capacity, (int) (t + toAdd)));
                lastRefillEpochMs = now;
            }
        }
    }
}
