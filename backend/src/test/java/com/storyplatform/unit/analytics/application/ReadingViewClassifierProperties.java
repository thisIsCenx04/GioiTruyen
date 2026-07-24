package com.storyplatform.unit.analytics.application;

import com.storyplatform.analytics.application.RawReadingEvent;
import com.storyplatform.analytics.application.ReadingViewClassifier;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ReadingViewClassifierProperties {

    private final ReadingViewClassifier classifier =
            new ReadingViewClassifier();

    @Property(tries = 200)
    void validityAlwaysMatchesTheAbsenceOfReasonCodes(
            @ForAll boolean duplicate,
            @ForAll boolean selfView,
            @ForAll boolean bot,
            @ForAll boolean zeroActiveProgress
    ) {
        Set<String> signals = bot
                ? Set.of("AUTOMATION_PATTERN")
                : Set.of();
        var result = classifier.classify(
                new ReadingViewClassifier.Candidate(
                        "event",
                        "fingerprint",
                        "session",
                        "actor",
                        "story",
                        "chapter",
                        RawReadingEvent.Kind.HEARTBEAT,
                        zeroActiveProgress ? 50 : 0,
                        zeroActiveProgress ? 0 : 10,
                        duplicate,
                        selfView,
                        signals
                ),
                Instant.EPOCH
        );

        assertThat(result.valid()).isEqualTo(result.reasons().isEmpty());
        assertThat(result.ruleVersion())
                .isEqualTo(ReadingViewClassifier.RULE_VERSION);
        assertThat(result.reasons().contains("DUPLICATE"))
                .isEqualTo(duplicate);
        assertThat(result.reasons().contains("SELF_VIEW"))
                .isEqualTo(selfView);
        assertThat(result.reasons().contains("AUTOMATION_PATTERN"))
                .isEqualTo(bot);
        assertThat(result.reasons().contains("ZERO_ACTIVE_PROGRESS"))
                .isEqualTo(zeroActiveProgress);
    }

    @Property(tries = 50)
    void completionNeverGetsHeartbeatOnlyReason(
            @ForAll double position
    ) {
        var result = classifier.classify(
                new ReadingViewClassifier.Candidate(
                        "event",
                        "fingerprint",
                        "session",
                        "actor",
                        "story",
                        "chapter",
                        RawReadingEvent.Kind.COMPLETION,
                        position,
                        null,
                        false,
                        false,
                        Set.of()
                ),
                Instant.EPOCH
        );

        assertThat(result.reasons())
                .doesNotContain("ZERO_ACTIVE_PROGRESS");
    }
}
