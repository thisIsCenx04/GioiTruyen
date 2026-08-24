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

    // Budgets are sized for how the SPA actually loads: one home page render
    // fans out 5 parallel calls, and a reader clicking through chapters can
    // stack several of those within a second. The old burst ceiling of 15/s
    // rejected ordinary browsing, and because the frontend swallows failures
    // into empty fallbacks a 429 surfaced as a blank page rather than an error.
    //
    // Readers also share public IPs behind carrier NAT and office gateways, so
    // a per-IP cap tuned for one person throttles a whole building. Reads are
    // therefore generous; writes and auth stay tight, since those are what
    // abuse actually targets.
    private static final int MAX_BURST_PER_SECOND = 60;
    private static final int MAX_AUTH_PER_MINUTE = 15;
    private static final int MAX_MUTATION_PER_MINUTE = 60;
    private static final int MAX_GENERAL_PER_MINUTE = 600;

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
            // A burst breach clears within a second; a minute breach does not.
            // Telling the client which it was lets a retry succeed promptly
            // instead of waiting out a full minute for a one-second overage.
            sendTooManyRequestsResponse(response, tracker.retryAfterSeconds(now));
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Địa chỉ dùng làm khoá hạn mức.
     *
     * <p>Bản cũ đọc thẳng phần tử đầu của X-Forwarded-For, mà phần đó do chính
     * người gọi viết ra. Đổi một dòng tiêu đề mỗi lần gọi là mỗi lần được cấp một
     * ô đếm mới, nên toàn bộ bộ giới hạn này coi như không tồn tại. Xem
     * {@link ClientIp} để biết vì sao phải lấy đầu bên kia.
     */
    private String extractClientIp(HttpServletRequest request) {
        return ClientIp.of(request);
    }

    private void sendTooManyRequestsResponse(HttpServletResponse response, long retryAfterSeconds)
            throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Retry-After", Long.toString(retryAfterSeconds));

        String jsonBody = """
                {
                  "type": "urn:problem:story-platform:too-many-requests",
                  "title": "Quá nhiều yêu cầu",
                  "status": 429,
                  "detail": "Tần suất truy cập quá cao. Vui lòng thử lại sau %d giây.",
                  "code": "TOO_MANY_REQUESTS",
                  "retryAfterSeconds": %d
                }
                """.formatted(retryAfterSeconds, retryAfterSeconds);
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

        /** True when the last rejection was the 1s burst cap rather than a 1m cap. */
        private boolean lastBreachWasBurst;

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

            boolean isAuth = path.startsWith("/auth/") || path.contains("/login") || path.contains("/register");
            boolean isMutation = "POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)
                    || "DELETE".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method);

            // Every applicable budget is tested before any is spent. Incrementing
            // as we went meant a request rejected by a later check had already
            // consumed the earlier counters, so one blocked call shortened every
            // other budget too.
            if (requestsThisSecond.get() >= MAX_BURST_PER_SECOND) {
                lastBreachWasBurst = true;
                return false;
            }
            if (requestsThisMinute.get() >= MAX_GENERAL_PER_MINUTE
                    || (isAuth && authRequestsThisMinute.get() >= MAX_AUTH_PER_MINUTE)
                    || (isMutation && mutationRequestsThisMinute.get() >= MAX_MUTATION_PER_MINUTE)) {
                lastBreachWasBurst = false;
                return false;
            }

            requestsThisSecond.incrementAndGet();
            requestsThisMinute.incrementAndGet();
            if (isAuth) {
                authRequestsThisMinute.incrementAndGet();
            }
            if (isMutation) {
                mutationRequestsThisMinute.incrementAndGet();
            }
            return true;
        }

        /** Seconds until the breached window reopens; at least 1 so clients back off. */
        synchronized long retryAfterSeconds(long now) {
            long windowStart = lastBreachWasBurst ? secondWindowStart : minuteWindowStart;
            long windowLength = lastBreachWasBurst ? 1000L : 60_000L;
            return Math.max(1L, (windowLength - (now - windowStart) + 999L) / 1000L);
        }

        boolean isStale(long now) {
            return now - minuteWindowStart > 300_000L;
        }
    }
}
