package com.storyplatform.publishing.application;

import com.storyplatform.publishing.application.PublishingPrecheckEngine
        .CheckResult;
import com.storyplatform.publishing.application.PublishingPrecheckEngine
        .Outcome;
import com.storyplatform.publishing.application.PublishingPrecheckEngine
        .Rule;
import com.storyplatform.publishing.application.port
        .PublishingPrecheckRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class PublishingPrecheckService
        implements PublishingPrecheckOperations {

    public static final String POLICY_VERSION = "publishing-precheck-v2";

    private final PublishingPrecheckRepository repository;
    private final PublishingPrecheckEngine engine;
    private final Clock clock;
    private final Duration leaseDuration;
    private final Duration checkTimeout;

    public PublishingPrecheckService(
            PublishingPrecheckRepository repository,
            PublishingPrecheckEngine engine,
            Clock clock,
            Duration leaseDuration,
            Duration checkTimeout
    ) {
        this.repository = Objects.requireNonNull(
                repository,
                "repository"
        );
        this.engine = Objects.requireNonNull(engine, "engine");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.leaseDuration = positive(leaseDuration, "leaseDuration");
        this.checkTimeout = positive(checkTimeout, "checkTimeout");
    }

    @Override
    public boolean processNext(String workerId) {
        if (workerId == null || workerId.isBlank()) {
            throw new IllegalArgumentException("workerId is required");
        }
        Instant startedAt = clock.instant();
        var claimed = repository.claim(
                workerId,
                startedAt,
                startedAt.plus(leaseDuration)
        );
        if (claimed.isEmpty()) {
            return false;
        }
        var review = claimed.orElseThrow();
        var evidence = repository.loadEvidence(review);
        List<CheckResult> checks;
        boolean manualFallback;
        if (evidence.isEmpty()) {
            checks = List.of(result(
                    Rule.SCHEMA,
                    Outcome.MANUAL,
                    "FROZEN_EVIDENCE_MISSING"
            ));
            manualFallback = true;
        } else {
            try {
                checks = List.copyOf(engine.check(
                        evidence.orElseThrow(),
                        startedAt.plus(checkTimeout)
                ));
                manualFallback = checks.stream().anyMatch(value ->
                        value.outcome() != Outcome.PASS
                );
            } catch (PublishingPrecheckTimeoutException exception) {
                checks = List.of(result(
                        Rule.SCHEMA,
                        Outcome.TIMEOUT,
                        "PRECHECK_TIMEOUT"
                ));
                manualFallback = true;
            } catch (RuntimeException exception) {
                checks = List.of(result(
                        Rule.SCHEMA,
                        Outcome.MANUAL,
                        "PRECHECK_UNAVAILABLE"
                ));
                manualFallback = true;
            }
        }
        repository.complete(
                review,
                workerId,
                checks,
                manualFallback,
                clock.instant()
        );
        return true;
    }

    private static CheckResult result(
            Rule rule,
            Outcome outcome,
            String code
    ) {
        return new CheckResult(rule, outcome, code, POLICY_VERSION);
    }

    private static Duration positive(Duration value, String field) {
        if (value == null
                || value.isZero()
                || value.isNegative()) {
            throw new IllegalArgumentException(
                    field + " must be positive"
            );
        }
        return value;
    }
}
