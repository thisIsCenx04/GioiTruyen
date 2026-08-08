package com.storyplatform.moderation.application;

import com.storyplatform.moderation.application.port.CopyrightCaseRepository;

import java.text.Normalizer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class CopyrightCaseService implements CopyrightCaseOperations {

    private final CopyrightCaseRepository repository;
    private final Clock clock;
    private final Duration responseSla;
    private final Duration holdDuration;
    private final Supplier<String> identifiers;

    public CopyrightCaseService(
            CopyrightCaseRepository repository,
            Clock clock,
            Duration responseSla,
            Duration holdDuration,
            Supplier<String> identifiers
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.responseSla = positive(responseSla, "responseSla");
        this.holdDuration = positive(holdDuration, "holdDuration");
        this.identifiers = Objects.requireNonNull(
                identifiers,
                "identifiers"
        );
    }

    @Override
    public CopyrightCaseView create(
            String claimantId,
            CreateCopyrightCase command
    ) {
        String claimant = uuid(claimantId, "claimantId");
        if (command == null) {
            throw invalid("A copyright claim is required.");
        }
        String story = uuid(command.storyId(), "storyId");
        String name = text(command.claimantName(), 160);
        String statement = text(command.statement(), 5000);
        List<String> evidence = command.evidenceMediaIds();
        if (evidence.isEmpty()
                || evidence.size() > 10
                || new HashSet<>(evidence).size() != evidence.size()
                || evidence.stream().anyMatch(value -> !isUuid(value))) {
            throw invalid("Between 1 and 10 unique evidence assets are required.");
        }
        if (!repository.storyIsPublic(story)) {
            throw failure(
                    "COPYRIGHT_STORY_NOT_FOUND",
                    "The public story was not found.",
                    CopyrightCaseException.Kind.NOT_FOUND
            );
        }
        if (!repository.evidenceIsPrivateAndOwned(claimant, evidence)) {
            throw failure(
                    "COPYRIGHT_EVIDENCE_FORBIDDEN",
                    "Evidence must be ready, private and owned by the claimant.",
                    CopyrightCaseException.Kind.FORBIDDEN
            );
        }
        Instant now = clock.instant();
        CopyrightCaseView candidate = new CopyrightCaseView(
                uuid(identifiers.get(), "caseId"),
                story,
                claimant,
                name,
                statement,
                evidence,
                "PENDING",
                now,
                now.plus(responseSla),
                now.plus(holdDuration),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        var result = repository.createAndHold(candidate);
        if (result.outcome() != CopyrightCaseRepository.Outcome.SUCCESS) {
            throw failure(
                    result.outcome() == CopyrightCaseRepository.Outcome.DUPLICATE
                            ? "COPYRIGHT_CASE_EXISTS"
                            : "COPYRIGHT_STORY_CHANGED",
                    "The claim conflicts with the current story state.",
                    CopyrightCaseException.Kind.CONFLICT
            );
        }
        return result.copyrightCase();
    }

    @Override
    public CopyrightCaseView appeal(
            String actorId,
            String caseId,
            String statement
    ) {
        String actor = uuid(actorId, "actorId");
        String id = uuid(caseId, "caseId");
        var result = repository.appeal(
                id,
                actor,
                text(statement, 4000),
                clock.instant()
        );
        if (result.outcome() != CopyrightCaseRepository.Outcome.SUCCESS) {
            throw failure(
                    "COPYRIGHT_APPEAL_CONFLICT",
                    "The case is not appealable by this account.",
                    result.outcome() == CopyrightCaseRepository.Outcome.DUPLICATE
                            ? CopyrightCaseException.Kind.CONFLICT
                            : CopyrightCaseException.Kind.FORBIDDEN
            );
        }
        return result.copyrightCase();
    }

    @Override
    public CopyrightCaseView decide(
            String reviewerId,
            String caseId,
            Decision decision,
            String reasonCode,
            String note
    ) {
        String reviewer = uuid(reviewerId, "reviewerId");
        String id = uuid(caseId, "caseId");
        if (decision == null
                || reasonCode == null
                || !reasonCode.matches("[A-Z][A-Z0-9_]{2,63}")) {
            throw invalid("Decision and reasonCode are required.");
        }
        var result = repository.decide(
                id,
                reviewer,
                decision,
                reasonCode,
                text(note, 2000),
                clock.instant()
        );
        if (result.outcome() != CopyrightCaseRepository.Outcome.SUCCESS) {
            throw failure(
                    "COPYRIGHT_DECISION_CONFLICT",
                    "The case is already final or the story changed.",
                    CopyrightCaseException.Kind.CONFLICT
            );
        }
        return result.copyrightCase();
    }

    private static Duration positive(Duration value, String field) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }

    private static String text(String value, int maximum) {
        String normalized = value == null
                ? ""
                : Normalizer.normalize(value, Normalizer.Form.NFC).trim()
                .replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                .replaceAll("(?s)<[^>]+>", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (normalized.isBlank() || normalized.length() > maximum) {
            throw invalid("Required text is outside the allowed length.");
        }
        return normalized;
    }

    private static boolean isUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static String uuid(String value, String field) {
        if (!isUuid(value)) {
            throw invalid(field + " must be a UUID.");
        }
        return UUID.fromString(value).toString();
    }

    private static CopyrightCaseException invalid(String message) {
        return failure(
                "COPYRIGHT_CASE_INVALID",
                message,
                CopyrightCaseException.Kind.INVALID
        );
    }

    private static CopyrightCaseException failure(
            String code,
            String message,
            CopyrightCaseException.Kind kind
    ) {
        return new CopyrightCaseException(code, message, kind);
    }
}
