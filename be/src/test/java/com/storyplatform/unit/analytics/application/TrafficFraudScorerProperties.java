package com.storyplatform.unit.analytics.application;

import com.storyplatform.analytics.application.TrafficFraudScorer;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TrafficFraudScorerProperties {

    private static final List<String> SIGNALS = List.of(
            "DUPLICATE",
            "SELF_VIEW",
            "ZERO_ACTIVE_PROGRESS",
            "EVENT_TIME_REGRESSION",
            "IMPOSSIBLE_PROGRESS",
            "ACTIVE_TIME_EXCEEDS_CADENCE"
    );
    private final TrafficFraudScorer scorer = new TrafficFraudScorer();

    @Property(tries = 100)
    void noSingleSignalCanCreateAHold(@ForAll int value) {
        String signal = SIGNALS.get(Math.floorMod(value, SIGNALS.size()));
        var score = scorer.score(Set.of(signal), Instant.EPOCH);

        assertThat(score.decision())
                .isNotEqualTo(
                        TrafficFraudScorer.Decision.HOLD_FOR_REVIEW
                );
        assertThat(score.contributions()).containsKey(signal);
    }

    @Property(tries = 100)
    void addingKnownSignalsNeverReducesScore(
            @ForAll boolean duplicate,
            @ForAll boolean selfView,
            @ForAll boolean impossible
    ) {
        Set<String> selected = new LinkedHashSet<>();
        if (duplicate) {
            selected.add("DUPLICATE");
        }
        if (selfView) {
            selected.add("SELF_VIEW");
        }
        if (impossible) {
            selected.add("IMPOSSIBLE_PROGRESS");
        }

        int baseline = scorer.score(Set.of(), Instant.EPOCH).value();
        int combined = scorer.score(selected, Instant.EPOCH).value();

        assertThat(combined).isGreaterThanOrEqualTo(baseline);
    }

    @Test
    void requiresMultipleIndependentSignalsForHoldAndIgnoresUnknowns() {
        var held = scorer.score(Set.of(
                "SELF_VIEW",
                "IMPOSSIBLE_PROGRESS",
                "ACTIVE_TIME_EXCEEDS_CADENCE"
        ), Instant.EPOCH);
        assertThat(held.value()).isEqualTo(90);
        assertThat(held.decision())
                .isEqualTo(TrafficFraudScorer.Decision.HOLD_FOR_REVIEW);

        var unknown = scorer.score(Set.of("PROTECTED_ATTRIBUTE"), Instant.EPOCH);
        assertThat(unknown.value()).isZero();
        assertThat(unknown.contributions()).isEmpty();
        assertThat(unknown.decision())
                .isEqualTo(TrafficFraudScorer.Decision.PASS);
    }
}
