package com.storyplatform.shared.cache;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;

/**
 * Enforces bounded cache TTLs and adds jitter to reduce synchronized expiry.
 */
public final class RedisTtlPolicy {

    private final Duration minimum;
    private final Duration maximum;
    private final double jitterRatio;
    private final DoubleSupplier random;

    public RedisTtlPolicy(
            Duration minimum,
            Duration maximum,
            double jitterRatio
    ) {
        this(
                minimum,
                maximum,
                jitterRatio,
                () -> ThreadLocalRandom.current().nextDouble()
        );
    }

    public RedisTtlPolicy(
            Duration minimum,
            Duration maximum,
            double jitterRatio,
            DoubleSupplier random
    ) {
        this.minimum = requirePositive(minimum, "minimum");
        this.maximum = requirePositive(maximum, "maximum");
        if (this.minimum.compareTo(this.maximum) > 0) {
            throw new IllegalArgumentException(
                    "minimum TTL must not exceed maximum TTL"
            );
        }
        if (jitterRatio < 0.0 || jitterRatio > 0.5) {
            throw new IllegalArgumentException(
                    "TTL jitter ratio must be between 0.0 and 0.5"
            );
        }
        this.jitterRatio = jitterRatio;
        this.random = Objects.requireNonNull(random, "random");
    }

    public Duration apply(Duration requested) {
        Duration bounded = requirePositive(requested, "requested");
        if (bounded.compareTo(minimum) < 0
                || bounded.compareTo(maximum) > 0) {
            throw new IllegalArgumentException(
                    "Requested cache TTL is outside the configured range"
            );
        }

        double sample = random.getAsDouble();
        if (sample < 0.0 || sample > 1.0) {
            throw new IllegalStateException(
                    "TTL random source must return a value from 0.0 to 1.0"
            );
        }
        double multiplier = 1.0
                + ((sample * 2.0) - 1.0) * jitterRatio;
        Duration jittered = Duration.ofMillis(
                Math.round(bounded.toMillis() * multiplier)
        );
        if (jittered.compareTo(minimum) < 0) {
            return minimum;
        }
        if (jittered.compareTo(maximum) > 0) {
            return maximum;
        }
        return jittered;
    }

    private static Duration requirePositive(
            Duration value,
            String name
    ) {
        Objects.requireNonNull(value, name);
        if (!value.isPositive()) {
            throw new IllegalArgumentException(name + " TTL must be positive");
        }
        return value;
    }
}
