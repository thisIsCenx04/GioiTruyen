package com.storyplatform.unit.shared.observability;

import com.storyplatform.shared.observability.OutboxTelemetry;
import com.storyplatform.shared.observability.TraceContextPropagation;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboxTelemetryTest {

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final ObservationRegistry observations =
            ObservationRegistry.create();
    private final OutboxTelemetry telemetry = new OutboxTelemetry(
            observations,
            new TraceContextPropagation(OpenTelemetry.noop())
    );

    @BeforeEach
    void registerMetricsHandler() {
        observations.observationConfig().observationHandler(
                new DefaultMeterObservationHandler(meters)
        );
    }

    @Test
    void recordsBoundedMessageOutcomeMetric() {
        try (OutboxTelemetry.Attempt attempt = telemetry.startAttempt(
                "publishing.story.published",
                1,
                Map.of()
        )) {
            attempt.outcome(OutboxTelemetry.Outcome.PROCESSED);
        }

        assertThat(meters.get("story.outbox.process")
                .tag("event.type", "publishing.story.published")
                .tag("event.version", "1")
                .tag("outcome", "processed")
                .timer()
                .count()).isEqualTo(1);
    }

    @Test
    void pollFailureIsCountedWithoutRecordingExceptionDetails() {
        assertThatThrownBy(() -> telemetry.observePoll(() -> {
            throw new IllegalStateException("database password");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(meters.get("story.outbox.poll")
                .tag("outcome", "failed")
                .timer()
                .count()).isEqualTo(1);
        assertThat(meters.getMeters())
                .allSatisfy(meter -> assertThat(
                        meter.getId().toString()
                ).doesNotContain("database password"));
    }
}
