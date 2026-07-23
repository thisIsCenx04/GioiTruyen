package com.storyplatform.shared.observability;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Map;
import java.util.function.IntSupplier;

/**
 * Low-cardinality, payload-free telemetry for the transactional outbox.
 */
public final class OutboxTelemetry {

    static final String POLL_OBSERVATION = "story.outbox.poll";
    static final String PROCESS_OBSERVATION = "story.outbox.process";

    private static final Logger LOGGER = LoggerFactory.getLogger(
            OutboxTelemetry.class
    );

    private final ObservationRegistry registry;
    private final TraceContextPropagation traceContextPropagation;

    public OutboxTelemetry(
            ObservationRegistry registry,
            TraceContextPropagation traceContextPropagation
    ) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.traceContextPropagation = Objects.requireNonNull(
                traceContextPropagation,
                "traceContextPropagation"
        );
    }

    public int observePoll(IntSupplier poll) {
        Observation observation = Observation
                .createNotStarted(POLL_OBSERVATION, registry)
                .start();
        String outcome = "completed";
        try (Observation.Scope ignored = observation.openScope()) {
            return poll.getAsInt();
        } catch (RuntimeException exception) {
            outcome = "failed";
            throw exception;
        } finally {
            observation.lowCardinalityKeyValue("outcome", outcome);
            observation.stop();
        }
    }

    public Attempt startAttempt(
            String eventType,
            int eventVersion,
            Map<String, String> traceContext
    ) {
        String safeEventType = TelemetryValueSanitizer.eventType(eventType);
        String safeEventVersion = TelemetryValueSanitizer.eventVersion(
                eventVersion
        );
        Scope parentScope = traceContextPropagation.restore(traceContext);
        Observation observation = Observation
                .createNotStarted(PROCESS_OBSERVATION, registry)
                .contextualName("outbox " + safeEventType)
                .lowCardinalityKeyValue("event.type", safeEventType)
                .lowCardinalityKeyValue(
                        "event.version",
                        safeEventVersion
                )
                .start();
        return new Attempt(
                observation,
                observation.openScope(),
                parentScope,
                safeEventType,
                safeEventVersion
        );
    }

    public enum Outcome {
        PROCESSED("processed"),
        RETRY_SCHEDULED("retry"),
        DEAD_LETTERED("dead_letter"),
        FAILED("failed");

        private final String tag;

        Outcome(String tag) {
            this.tag = tag;
        }
    }

    public static final class Attempt implements AutoCloseable {

        private final Observation observation;
        private final Observation.Scope scope;
        private final Scope parentScope;
        private final String eventType;
        private final String eventVersion;
        private Outcome outcome = Outcome.FAILED;
        private boolean closed;

        private Attempt(
                Observation observation,
                Observation.Scope scope,
                Scope parentScope,
                String eventType,
                String eventVersion
        ) {
            this.observation = observation;
            this.scope = scope;
            this.parentScope = parentScope;
            this.eventType = eventType;
            this.eventVersion = eventVersion;
        }

        public void outcome(Outcome outcome) {
            this.outcome = Objects.requireNonNull(outcome, "outcome");
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            observation.lowCardinalityKeyValue("outcome", outcome.tag);
            logOutcome();
            scope.close();
            observation.stop();
            parentScope.close();
        }

        private void logOutcome() {
            var event = outcome == Outcome.PROCESSED
                    ? LOGGER.atInfo()
                    : LOGGER.atWarn();
            event.addKeyValue("event.type", eventType)
                    .addKeyValue("event.version", eventVersion)
                    .addKeyValue("outcome", outcome.tag)
                    .log("Outbox delivery finished");
        }
    }
}
