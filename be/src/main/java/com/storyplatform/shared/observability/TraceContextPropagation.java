package com.storyplatform.shared.observability;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Persists only W3C trace context across asynchronous boundaries.
 *
 * <p>Baggage is deliberately excluded because it may contain user-controlled
 * or sensitive values.</p>
 */
public final class TraceContextPropagation {

    private static final Set<String> ALLOWED_FIELDS = Set.of(
            "traceparent",
            "tracestate"
    );
    private static final int MAX_VALUE_LENGTH = 512;

    private final TextMapPropagator propagator;

    public TraceContextPropagation(OpenTelemetry openTelemetry) {
        this.propagator = Objects.requireNonNull(
                openTelemetry,
                "openTelemetry"
        ).getPropagators().getTextMapPropagator();
    }

    public Map<String, String> capture() {
        Map<String, String> carrier = new LinkedHashMap<>();
        propagator.inject(
                Context.current(),
                carrier,
                (target, key, value) -> {
                    String normalizedKey = key.toLowerCase(Locale.ROOT);
                    if (ALLOWED_FIELDS.contains(normalizedKey)
                            && value.length() <= MAX_VALUE_LENGTH) {
                        target.put(normalizedKey, value);
                    }
                }
        );
        return Map.copyOf(carrier);
    }

    public Scope restore(Map<String, String> carrier) {
        Map<String, String> safeCarrier = sanitize(carrier);
        Context extracted = propagator.extract(
                Context.root(),
                safeCarrier,
                new TextMapGetter<>() {
                    @Override
                    public Iterable<String> keys(
                            Map<String, String> source
                    ) {
                        return source.keySet();
                    }

                    @Override
                    public String get(
                            Map<String, String> source,
                            String key
                    ) {
                        return source.get(key.toLowerCase(Locale.ROOT));
                    }
                }
        );
        return extracted.makeCurrent();
    }

    private static Map<String, String> sanitize(
            Map<String, String> carrier
    ) {
        if (carrier == null || carrier.isEmpty()) {
            return Map.of();
        }
        Map<String, String> safe = new LinkedHashMap<>();
        List.copyOf(ALLOWED_FIELDS).forEach(field -> {
            String value = carrier.get(field);
            if (value != null && value.length() <= MAX_VALUE_LENGTH) {
                safe.put(field, value);
            }
        });
        return Map.copyOf(safe);
    }
}
