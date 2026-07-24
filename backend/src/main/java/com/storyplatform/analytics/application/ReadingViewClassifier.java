package com.storyplatform.analytics.application;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class ReadingViewClassifier {

    public static final String RULE_VERSION = "reading-view-2026.1";

    public Classification classify(Candidate candidate, Instant classifiedAt) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(classifiedAt, "classifiedAt");
        Set<String> reasons = new LinkedHashSet<>();
        if (candidate.duplicate()) {
            reasons.add("DUPLICATE");
        }
        if (candidate.selfView()) {
            reasons.add("SELF_VIEW");
        }
        reasons.addAll(candidate.botSignals());
        if (candidate.kind() == RawReadingEvent.Kind.HEARTBEAT
                && candidate.activeSeconds() != null
                && candidate.activeSeconds() == 0
                && candidate.position() > 0) {
            reasons.add("ZERO_ACTIVE_PROGRESS");
        }
        return new Classification(
                candidate.eventId(),
                candidate.fingerprint(),
                candidate.sessionRef(),
                candidate.actorRef(),
                candidate.storyId(),
                candidate.chapterId(),
                candidate.kind(),
                RULE_VERSION,
                reasons.isEmpty(),
                Set.copyOf(reasons),
                classifiedAt
        );
    }

    public record Candidate(
            String eventId,
            String fingerprint,
            String sessionRef,
            String actorRef,
            String storyId,
            String chapterId,
            RawReadingEvent.Kind kind,
            double position,
            Integer activeSeconds,
            boolean duplicate,
            boolean selfView,
            Set<String> botSignals
    ) {
        public Candidate {
            Objects.requireNonNull(eventId, "eventId");
            Objects.requireNonNull(fingerprint, "fingerprint");
            Objects.requireNonNull(sessionRef, "sessionRef");
            Objects.requireNonNull(actorRef, "actorRef");
            Objects.requireNonNull(storyId, "storyId");
            Objects.requireNonNull(chapterId, "chapterId");
            Objects.requireNonNull(kind, "kind");
            botSignals = Set.copyOf(botSignals);
        }
    }

    public record Classification(
            String eventId,
            String fingerprint,
            String sessionRef,
            String actorRef,
            String storyId,
            String chapterId,
            RawReadingEvent.Kind kind,
            String ruleVersion,
            boolean valid,
            Set<String> reasons,
            Instant classifiedAt
    ) {
        public Classification {
            reasons = Set.copyOf(reasons);
        }
    }
}
