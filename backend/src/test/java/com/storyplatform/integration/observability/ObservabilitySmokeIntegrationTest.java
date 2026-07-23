package com.storyplatform.integration.observability;

import com.storyplatform.shared.observability.TraceContextPropagation;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Scope;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "management.tracing.sampling.probability=1.0",
        "management.tracing.export.otlp.enabled=false",
        "management.otlp.metrics.export.enabled=false"
})
class ObservabilitySmokeIntegrationTest {

    @Autowired
    private ObservationRegistry observations;

    @Autowired
    private Tracer tracer;

    @Autowired
    private OpenTelemetry openTelemetry;

    @Autowired
    private TraceContextPropagation traceContextPropagation;

    @Test
    void observationCreatesAnOpenTelemetryBackedTrace() {
        assertThat(openTelemetry).isNotNull();

        Observation.createNotStarted(
                "story.observability.smoke",
                observations
        ).observe(() -> {
            assertThat(tracer.currentSpan()).isNotNull();
            assertThat(tracer.currentSpan().context().traceId())
                    .matches("[0-9a-f]{32}")
                    .isNotEqualTo("0".repeat(32));
        });
    }

    @Test
    void persistedW3cContextContinuesTheProducerTrace() {
        AtomicReference<String> producerTraceId = new AtomicReference<>();
        AtomicReference<Map<String, String>> carrier =
                new AtomicReference<>();

        Observation.createNotStarted(
                "story.outbox.append",
                observations
        ).observe(() -> {
            producerTraceId.set(tracer.currentSpan().context().traceId());
            carrier.set(traceContextPropagation.capture());
        });

        assertThat(carrier.get()).containsKey("traceparent");
        assertThat(carrier.get()).doesNotContainKey("baggage");

        try (Scope ignored = traceContextPropagation.restore(carrier.get())) {
            Observation.createNotStarted(
                    "story.outbox.process",
                    observations
            ).observe(() -> assertThat(
                    tracer.currentSpan().context().traceId()
            ).isEqualTo(producerTraceId.get()));
        }
    }
}
