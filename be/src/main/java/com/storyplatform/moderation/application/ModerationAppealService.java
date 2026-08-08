package com.storyplatform.moderation.application;

import com.storyplatform.moderation.application.port.ModerationAppealRepository;

import java.text.Normalizer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class ModerationAppealService
        implements ModerationAppealOperations {

    private final ModerationAppealRepository repository;
    private final Clock clock;
    private final Duration window;
    private final Supplier<String> identifiers;

    public ModerationAppealService(
            ModerationAppealRepository repository,
            Clock clock,
            Duration window,
            Supplier<String> identifiers
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.window = Objects.requireNonNull(window, "window");
        this.identifiers = Objects.requireNonNull(
                identifiers,
                "identifiers"
        );
        if (window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("window must be positive");
        }
    }

    @Override
    public AppealView create(
            String actorId,
            String reviewId,
            String statement
    ) {
        String actor = uuid(actorId, "actorId");
        String review = uuid(reviewId, "reviewId");
        String safeStatement = text(statement, 4000, true);
        ModerationAppealRepository.EligibleReview eligible = repository
                .eligibleReview(review, actor)
                .orElseThrow(() -> failure(
                        "APPEAL_NOT_ELIGIBLE",
                        "The case is not appealable by this account.",
                        ModerationAppealException.Kind.FORBIDDEN
                ));
        Instant now = clock.instant();
        Instant deadline = eligible.decidedAt().plus(window);
        if (now.isAfter(deadline)) {
            throw failure(
                    "APPEAL_WINDOW_EXPIRED",
                    "The appeal window has expired.",
                    ModerationAppealException.Kind.CONFLICT
            );
        }
        var result = repository.createIfAbsent(
                identifiers.get(),
                eligible,
                actor,
                safeStatement,
                now,
                deadline
        );
        if (result.outcome() == ModerationAppealRepository.Outcome.DUPLICATE) {
            throw failure(
                    "APPEAL_ALREADY_EXISTS",
                    "This case already has an appeal.",
                    ModerationAppealException.Kind.CONFLICT
            );
        }
        return result.appeal();
    }

    @Override
    public AppealView decide(
            String reviewerId,
            String reviewId,
            String appealId,
            AppealDecision decision,
            String reasonCode,
            String note
    ) {
        String reviewer = uuid(reviewerId, "reviewerId");
        String review = uuid(reviewId, "reviewId");
        String appeal = uuid(appealId, "appealId");
        if (decision == null) {
            throw invalid("decision is required.");
        }
        String reason = code(reasonCode);
        String safeNote = text(note, 2000, true);
        var result = repository.resolve(
                review,
                appeal,
                reviewer,
                decision,
                reason,
                safeNote,
                clock.instant()
        );
        if (result.outcome() != ModerationAppealRepository.Outcome.SUCCESS) {
            throw failure(
                    "APPEAL_DECISION_CONFLICT",
                    "The appeal is final or reviewer separation failed.",
                    ModerationAppealException.Kind.CONFLICT
            );
        }
        return result.appeal();
    }

    private static String code(String value) {
        if (value == null || !value.matches("[A-Z][A-Z0-9_]{2,63}")) {
            throw invalid("reasonCode is invalid.");
        }
        return value;
    }

    private static String text(String value, int maximum, boolean required) {
        String normalized = value == null
                ? ""
                : Normalizer.normalize(value, Normalizer.Form.NFC).trim();
        normalized = normalized
                .replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                .replaceAll("(?s)<[^>]+>", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if ((required && normalized.isBlank())
                || normalized.length() > maximum) {
            throw invalid("Text is required and must be within limits.");
        }
        return normalized;
    }

    private static String uuid(String value, String field) {
        try {
            return UUID.fromString(value).toString();
        } catch (RuntimeException exception) {
            throw invalid(field + " must be a UUID.");
        }
    }

    private static ModerationAppealException invalid(String message) {
        return failure(
                "APPEAL_INVALID",
                message,
                ModerationAppealException.Kind.INVALID
        );
    }

    private static ModerationAppealException failure(
            String code,
            String message,
            ModerationAppealException.Kind kind
    ) {
        return new ModerationAppealException(code, message, kind);
    }
}
