package com.storyplatform.auth.application;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * Throttles password guessing against a single account or from a single client.
 *
 * <p>Counters live in memory: the platform runs one backend instance, and a
 * restart clearing the counters is acceptable for this threat model. Moving to
 * several instances would mean backing this with Redis.
 */
@Component
public class LoginAttemptLimiter {

    /** Failures tolerated inside {@link #WINDOW} before the key is locked out. */
    static final int MAX_FAILURES = 5;

    /** Failures older than this stop counting, so a typo today is forgiven tomorrow. */
    static final Duration WINDOW = Duration.ofMinutes(15);

    /** How long a key stays blocked once it trips the limit. */
    static final Duration LOCKOUT = Duration.ofMinutes(15);

    /** Guards against unbounded growth if an attacker rotates keys. */
    private static final int MAX_TRACKED_KEYS = 10_000;

    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    private static final class Attempts {
        private final AtomicInteger failures = new AtomicInteger();
        /**
         * Seeded from the caller's timestamp, never {@code Instant.now()}: the
         * window is measured against the clock the caller passes in, so mixing
         * in wall-clock time made the first streak un-forgivable.
         */
        private volatile Instant firstFailureAt;
        private volatile Instant blockedUntil = Instant.EPOCH;

        Attempts(Instant now) {
            this.firstFailureAt = now;
        }
    }

    /**
     * @return seconds the caller must wait, or 0 when the attempt may proceed.
     */
    public long retryAfterSeconds(String key, Instant now) {
        Attempts entry = attempts.get(key);
        if (entry == null || now.isAfter(entry.blockedUntil)) {
            return 0;
        }
        return Math.max(1, Duration.between(now, entry.blockedUntil).toSeconds());
    }

    public void recordFailure(String key, Instant now) {
        if (attempts.size() >= MAX_TRACKED_KEYS) {
            attempts.entrySet().removeIf(entry ->
                    now.isAfter(entry.getValue().firstFailureAt.plus(WINDOW))
                            && now.isAfter(entry.getValue().blockedUntil));
        }

        Attempts entry = attempts.computeIfAbsent(key, ignored -> new Attempts(now));
        synchronized (entry) {
            // A stale window means this failure starts a fresh streak rather than
            // adding to one the user has already been forgiven for.
            if (now.isAfter(entry.firstFailureAt.plus(WINDOW))) {
                entry.failures.set(0);
                entry.firstFailureAt = now;
            }
            if (entry.failures.incrementAndGet() >= MAX_FAILURES) {
                entry.blockedUntil = now.plus(LOCKOUT);
                entry.failures.set(0);
                entry.firstFailureAt = now;
            }
        }
    }

    /** Clears the streak so a legitimate user is never penalised after signing in. */
    public void recordSuccess(String key) {
        attempts.remove(key);
    }
}
