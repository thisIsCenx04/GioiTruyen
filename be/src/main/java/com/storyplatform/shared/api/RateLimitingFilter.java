package com.storyplatform.shared.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Global Anti-Spam and Rate Limiting filter.
 * Prevents DDoS and API abuse using a sliding window per IP address.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public final class RateLimitingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitingFilter.class);

    // Limits per 1 minute window
    private static final int MAX_BURST_PER_SECOND = 15;
    private static final int MAX_AUTH_PER_MINUTE = 15;
    private static final int MAX_MUTATION_PER_MINUTE = 40;
    private static final int MAX_GENERAL_PER_MINUTE = 180;

    private static final long CLEANUP_INTERVAL_MS = 300_000L; // 5 minutes

    private final Map<String, IpRequestTracker> ipTrackers = new ConcurrentHashMap<>();
    private volatile long lastCleanupTime = System.currentTimeMillis();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator") || "/livez".equals(path) || "/readyz".equals(path);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        cleanupStaleEntriesIfNeeded();

        String ip = extractClientIp(request);
        String path = request.getRequestURI();
        String method = request.getMethod();

        long now = System.currentTimeMillis();
        IpRequestTracker tracker = ipTrackers.computeIfAbsent(ip, k -> new IpRequestTracker(now));

        if (!tracker.allowRequest(now, method, path)) {
            log.warn("Rate limit / Anti-spam triggered for IP={} path={} method={}", ip, path, method);
            sendTooManyRequestsResponse(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }
        return request.getRemoteAddr();
    }

    private void sendTooManyRequestsResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Retry-After", "60");

        String jsonBody = """
                {
                  "type": "urn:problem:story-platform:too-many-requests",
                  "title": "Quá nhiều yêu cầu",
                  "status": 429,
                  "detail": "Tần suất truy cập quá cao. Vui lòng thử lại sau 60 giây.",
                  "code": "TOO_MANY_REQUESTS"
                }
                """;
        response.getWriter().write(jsonBody);
    }

    private void cleanupStaleEntriesIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastCleanupTime > CLEANUP_INTERVAL_MS) {
            synchronized (this) {
                if (now - lastCleanupTime > CLEANUP_INTERVAL_MS) {
                    ipTrackers.entrySet().removeIf(e -> e.getValue().isStale(now));
                    lastCleanupTime = now;
                }
            }
        }
    }

    private static class IpRequestTracker {
        private long minuteWindowStart;
        private long secondWindowStart;
        private final AtomicInteger requestsThisSecond = new AtomicInteger(0);
        private final AtomicInteger requestsThisMinute = new AtomicInteger(0);
        private final AtomicInteger authRequestsThisMinute = new AtomicInteger(0);
        private final AtomicInteger mutationRequestsThisMinute = new AtomicInteger(0);

        IpRequestTracker(long now) {
            this.minuteWindowStart = now;
            this.secondWindowStart = now;
        }

        synchronized boolean allowRequest(long now, String method, String path) {
            // Reset 1-second burst window
            if (now - secondWindowStart >= 1000L) {
                secondWindowStart = now;
                requestsThisSecond.set(0);
            }

            // Reset 1-minute window
            if (now - minuteWindowStart >= 60000L) {
                minuteWindowStart = now;
                requestsThisMinute.set(0);
                authRequestsThisMinute.set(0);
                mutationRequestsThisMinute.set(0);
            }

            // Check burst per second limit
            if (requestsThisSecond.incrementAndGet() > MAX_BURST_PER_SECOND) {
                return false;
            }

            // Check total per minute limit
            if (requestsThisMinute.incrementAndGet() > MAX_GENERAL_PER_MINUTE) {
                return false;
            }

            // Check Auth endpoint limit
            boolean isAuth = path.startsWith("/auth/") || path.contains("/login") || path.contains("/register");
            if (isAuth && authRequestsThisMinute.incrementAndGet() > MAX_AUTH_PER_MINUTE) {
                return false;
            }

            // Check Mutation endpoint limit (POST, PUT, DELETE, PATCH)
            boolean isMutation = "POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)
                    || "DELETE".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method);
            if (isMutation && mutationRequestsThisMinute.incrementAndGet() > MAX_MUTATION_PER_MINUTE) {
                return false;
            }

            return true;
        }

        boolean isStale(long now) {
            return now - minuteWindowStart > 300_000L;
        }
    }
}
