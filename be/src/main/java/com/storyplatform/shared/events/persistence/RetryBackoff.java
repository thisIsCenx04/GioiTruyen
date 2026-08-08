package com.storyplatform.shared.events.persistence;

import java.time.Duration;

public class RetryBackoff {

    private final Duration initial;
    private final Duration maximum;

    public RetryBackoff(Duration initial, Duration maximum) {
        this.initial = initial;
        this.maximum = maximum;
    }

    public Duration delayForAttempt(int attempt) {
        if (attempt < 1) {
            throw new IllegalArgumentException("attempt must be positive");
        }
        long multiplier = 1L << Math.min(attempt - 1, 30);
        try {
            Duration candidate = initial.multipliedBy(multiplier);
            return candidate.compareTo(maximum) > 0 ? maximum : candidate;
        } catch (ArithmeticException exception) {
            return maximum;
        }
    }
}
