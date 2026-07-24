package com.storyplatform.analytics.application;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class TrafficFraudScorer {

    public static final String RULE_VERSION = "traffic-fraud-2026.1";
    private static final int REVIEW_THRESHOLD = 40;
    private static final int HOLD_THRESHOLD = 70;
    private static final Map<String, Integer> WEIGHTS = Map.of(
            "DUPLICATE", 20,
            "SELF_VIEW", 30,
            "ZERO_ACTIVE_PROGRESS", 15,
            "EVENT_TIME_REGRESSION", 15,
            "IMPOSSIBLE_PROGRESS", 35,
            "ACTIVE_TIME_EXCEEDS_CADENCE", 25
    );

    public Score score(Set<String> signals, Instant scoredAt) {
        Objects.requireNonNull(signals, "signals");
        Objects.requireNonNull(scoredAt, "scoredAt");
        Map<String, Integer> contributions = new LinkedHashMap<>();
        signals.stream().sorted().forEach(signal -> {
            Integer weight = WEIGHTS.get(signal);
            if (weight != null) {
                contributions.put(signal, weight);
            }
        });
        int value = contributions.values().stream()
                .mapToInt(Integer::intValue)
                .sum();
        Decision decision;
        if (value >= HOLD_THRESHOLD && contributions.size() >= 2) {
            decision = Decision.HOLD_FOR_REVIEW;
        } else if (value >= REVIEW_THRESHOLD) {
            decision = Decision.REVIEW;
        } else {
            decision = Decision.PASS;
        }
        return new Score(
                RULE_VERSION,
                value,
                decision,
                Map.copyOf(contributions),
                scoredAt
        );
    }

    public record Score(
            String ruleVersion,
            int value,
            Decision decision,
            Map<String, Integer> contributions,
            Instant scoredAt
    ) {
        public Score {
            contributions = Map.copyOf(contributions);
        }
    }

    public enum Decision {
        PASS,
        REVIEW,
        HOLD_FOR_REVIEW
    }
}
