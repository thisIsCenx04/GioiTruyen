package com.storyplatform.shared.cache;

import io.micrometer.core.instrument.MeterRegistry;

import java.util.Objects;

/**
 * Low-cardinality Redis metrics that never expose keys or cached values.
 */
public final class RedisCacheTelemetry {

    static final String OPERATION_COUNTER = "story.redis.operations";

    private final MeterRegistry meterRegistry;

    public RedisCacheTelemetry(MeterRegistry meterRegistry) {
        this.meterRegistry = Objects.requireNonNull(
                meterRegistry,
                "meterRegistry"
        );
    }

    void record(
            RedisNamespace namespace,
            String operation,
            String outcome
    ) {
        meterRegistry.counter(
                OPERATION_COUNTER,
                "namespace",
                namespace.keySegment(),
                "operation",
                operation,
                "outcome",
                outcome
        ).increment();
    }
}
