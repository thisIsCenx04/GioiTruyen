package com.storyplatform.identity.infrastructure.security;

import com.storyplatform.identity.application.port.LoginRiskLimiter;
import com.storyplatform.identity.infrastructure.LoginRiskProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryLoginRiskLimiter implements LoginRiskLimiter {

    private final LoginRiskProperties properties;
    private final Clock clock;
    private final Map<String, FailureCounter> failures = new ConcurrentHashMap<>();

    public InMemoryLoginRiskLimiter(LoginRiskProperties properties, Clock clock) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public boolean allow(String emailFingerprint, String addressFingerprint) {
        Instant now = clock.instant();
        return count("email:" + emailFingerprint, now) < properties.maxEmailFailures()
                && count("address:" + addressFingerprint, now) < properties.maxAddressFailures();
    }

    @Override
    public long retryAfterSeconds() {
        return properties.window().toSeconds();
    }

    @Override
    public void recordFailure(String emailFingerprint, String addressFingerprint) {
        Instant now = clock.instant();
        increment("email:" + emailFingerprint, now);
        increment("address:" + addressFingerprint, now);
    }

    @Override
    public void recordSuccess(String emailFingerprint, String addressFingerprint) {
        failures.remove("email:" + emailFingerprint);
    }

    private int count(String key, Instant now) {
        FailureCounter counter = failures.get(key);
        if (counter == null || !counter.expiresAt().isAfter(now)) {
            failures.remove(key);
            return 0;
        }
        return counter.count();
    }

    private void increment(String key, Instant now) {
        failures.compute(key, (ignored, current) -> {
            if (current == null || !current.expiresAt().isAfter(now)) {
                return new FailureCounter(1, now.plus(properties.window()));
            }
            return new FailureCounter(current.count() + 1, current.expiresAt());
        });
    }

    private record FailureCounter(int count, Instant expiresAt) {
    }
}
