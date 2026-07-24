package com.storyplatform.moderation.application;

import com.storyplatform.moderation.application.port
        .ModerationDecisionRepository;

import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ModerationDecisionService
        implements ModerationDecisionOperations {

    private final ModerationDecisionRepository repository;
    private final Clock clock;

    public ModerationDecisionService(
            ModerationDecisionRepository repository,
            Clock clock
    ) {
        this.repository = Objects.requireNonNull(
                repository,
                "repository"
        );
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public DecisionView decide(
            String reviewerId,
            String reviewId,
            long expectedVersion,
            DecisionCommand command
    ) {
        String reviewer = uuid(reviewerId, "reviewerId");
        String review = uuid(reviewId, "reviewId");
        if (expectedVersion < 1 || command == null
                || command.decision() == null) {
            throw invalid("A valid version and decision are required.");
        }
        String reason = normalizedCode(
                command.reasonCode(),
                "reasonCode"
        );
        String policyVersion = normalizedPolicy(
                command.policyVersion()
        );
        String note = normalizedNote(command.note());
        if (command.decision() != Decision.APPROVE
                && (note == null || note.isBlank())) {
            throw invalid(
                    "Request changes and reject decisions require a note."
            );
        }
        List<String> evidence = evidence(command.evidenceRefs());
        Instant now = clock.instant();
        ModerationDecisionRepository.Result result = repository.decide(
                review,
                reviewer,
                expectedVersion,
                new ModerationDecisionRepository.DecisionRecord(
                        command.decision(),
                        reason,
                        note,
                        evidence,
                        policyVersion
                ),
                now
        );
        if (result.outcome()
                != ModerationDecisionRepository.Outcome.SUCCESS) {
            String code = result.outcome()
                    == ModerationDecisionRepository.Outcome.STALE_TARGET
                    ? "REVIEW_TARGET_STALE"
                    : "REVIEW_ALREADY_DECIDED";
            throw new ModerationDecisionException(
                    code,
                    "The review or its frozen target is no longer decidable.",
                    ModerationDecisionException.Kind.CONFLICT
            );
        }
        return new DecisionView(
                review,
                result.state(),
                command.decision(),
                reason,
                policyVersion,
                reviewer,
                now,
                result.version()
        );
    }

    private static String normalizedCode(String value, String field) {
        if (value == null
                || !value.matches("[A-Z][A-Z0-9_]{2,63}")) {
            throw invalid(field + " is invalid.");
        }
        return value;
    }

    private static String normalizedPolicy(String value) {
        if (value == null
                || value.length() > 64
                || !value.matches("[a-z0-9][a-z0-9._-]+")) {
            throw invalid("policyVersion is invalid.");
        }
        return value;
    }

    private static String normalizedNote(String value) {
        if (value == null) {
            return null;
        }
        String normalized = Normalizer.normalize(
                value,
                Normalizer.Form.NFC
        ).trim();
        if (normalized.length() > 2000) {
            throw invalid("Decision note is too long.");
        }
        return normalized;
    }

    private static List<String> evidence(List<String> values) {
        List<String> safe = values == null ? List.of() : List.copyOf(values);
        if (safe.size() > 20
                || new HashSet<>(safe).size() != safe.size()
                || safe.stream().anyMatch(value ->
                value == null
                        || !value.matches(
                        "[A-Za-z0-9][A-Za-z0-9._:-]{0,127}"
                ))) {
            throw invalid("Evidence references are invalid.");
        }
        return safe;
    }

    private static String uuid(String value, String field) {
        try {
            return UUID.fromString(value).toString();
        } catch (RuntimeException exception) {
            throw invalid(field + " must be a UUID.");
        }
    }

    private static ModerationDecisionException invalid(String message) {
        return new ModerationDecisionException(
                "MODERATION_DECISION_INVALID",
                message,
                ModerationDecisionException.Kind.INVALID
        );
    }
}
