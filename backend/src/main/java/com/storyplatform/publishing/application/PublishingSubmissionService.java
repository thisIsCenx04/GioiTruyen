package com.storyplatform.publishing.application;

import com.storyplatform.publishing.application.port
        .PublishingSubmissionRepository;
import com.storyplatform.publishing.domain.PublishingReview;
import com.storyplatform.shared.events.IntegrationEvent;
import com.storyplatform.shared.events.persistence.OutboxAppender;
import com.storyplatform.teams.application.contract.TeamPermissionAuthorizer;
import com.storyplatform.teams.application.contract.TeamStatusDirectory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class PublishingSubmissionService
        implements PublishingSubmissionOperations {

    static final int MAXIMUM_CHAPTERS = 10_000;

    public static final String SUBMIT_PERMISSION = "story:submit";
    public static final String EVENT_TYPE = "publishing.story.submitted";

    private final TeamPermissionAuthorizer permissions;
    private final TeamStatusDirectory teams;
    private final PublishingSubmissionRepository submissions;
    private final OutboxAppender outbox;
    private final Supplier<String> identifiers;
    private final Clock clock;

    public PublishingSubmissionService(
            TeamPermissionAuthorizer permissions,
            TeamStatusDirectory teams,
            PublishingSubmissionRepository submissions,
            OutboxAppender outbox,
            Supplier<String> identifiers,
            Clock clock
    ) {
        this.permissions = Objects.requireNonNull(
                permissions,
                "permissions"
        );
        this.teams = Objects.requireNonNull(teams, "teams");
        this.submissions = Objects.requireNonNull(
                submissions,
                "submissions"
        );
        this.outbox = Objects.requireNonNull(outbox, "outbox");
        this.identifiers = Objects.requireNonNull(
                identifiers,
                "identifiers"
        );
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public SubmissionView submit(
            String actorId,
            String teamId,
            String storyId,
            String idempotencyKey
    ) {
        String key = idempotencyKey(idempotencyKey);
        if (!teams.isActive(teamId)
                || !permissions.allows(
                actorId,
                teamId,
                SUBMIT_PERMISSION
        )) {
            throw rejected(
                    "STORY_SUBMIT_FORBIDDEN",
                    "An active Team membership with story:submit is required.",
                    PublishingSubmissionException.Kind.FORBIDDEN
            );
        }
        String fingerprint = fingerprint(storyId);
        var replay = submissions.findReplay(teamId, key);
        if (replay.isPresent()) {
            PublishingReview previous = replay.orElseThrow();
            if (!previous.idempotencyFingerprint().equals(fingerprint)) {
                throw rejected(
                        "IDEMPOTENCY_KEY_REUSED",
                        "The idempotency key belongs to another request.",
                        PublishingSubmissionException.Kind.CONFLICT
                );
            }
            return view(previous);
        }

        PublishingSubmissionRepository.SubmissionCandidate candidate =
                submissions.findCandidate(teamId, storyId).orElseThrow(
                        () -> rejected(
                                "STORY_DRAFT_NOT_FOUND",
                                "The submittable Team story does not exist.",
                                PublishingSubmissionException.Kind.NOT_FOUND
                        )
                );
        if (candidate.chapterRevisions().isEmpty()
                || candidate.chapterRevisions().size()
                > MAXIMUM_CHAPTERS) {
            throw rejected(
                    "STORY_CHAPTER_REQUIRED",
                    "A story must have between 1 and 10,000 valid chapters.",
                    PublishingSubmissionException.Kind.UNPROCESSABLE
            );
        }
        Instant now = clock.instant();
        PublishingReview review = new PublishingReview(
                uuid(identifiers.get(), "reviewId"),
                PublishingReview.TargetType.STORY,
                candidate.storyId(),
                candidate.teamId(),
                candidate.storyRevision(),
                candidate.storyVersion(),
                candidate.chapterRevisions(),
                PublishingReview.State.AUTOMATED_CHECK_PENDING,
                actorId,
                now,
                1,
                key,
                fingerprint
        );
        if (!submissions.submit(candidate, review)) {
            throw rejected(
                    "STORY_SUBMISSION_STALE",
                    "The story revision changed while it was submitted.",
                    PublishingSubmissionException.Kind.CONFLICT
            );
        }
        outbox.append(new IntegrationEvent(
                UUID.fromString(uuid(identifiers.get(), "eventId")),
                EVENT_TYPE,
                1,
                now,
                key,
                "story",
                storyId,
                actorId,
                teamId,
                new StorySubmitted(
                        review.id(),
                        storyId,
                        review.submittedRevision(),
                        review.chapterRevisions().size()
                )
        ));
        return view(review);
    }

    private static SubmissionView view(PublishingReview review) {
        List<FrozenChapterRevision> chapters = review.chapterRevisions()
                .stream()
                .map(value -> new FrozenChapterRevision(
                        value.chapterId(),
                        value.revisionId(),
                        value.number()
                ))
                .toList();
        return new SubmissionView(
                review.id(),
                review.targetId(),
                review.submittedRevision(),
                chapters,
                review.state().name(),
                review.version(),
                review.submittedAt()
        );
    }

    private static String idempotencyKey(String value) {
        if (value == null
                || value.length() < 8
                || value.length() > 128
                || !value.matches("[A-Za-z0-9][A-Za-z0-9._:-]+")) {
            throw rejected(
                    "IDEMPOTENCY_KEY_INVALID",
                    "A valid Idempotency-Key is required.",
                    PublishingSubmissionException.Kind.INVALID
            );
        }
        return value;
    }

    private static String fingerprint(String storyId) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(
                            storyId.getBytes(StandardCharsets.UTF_8)
                    )
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable",
                    exception
            );
        }
    }

    private static String uuid(String value, String field) {
        try {
            return UUID.fromString(value).toString();
        } catch (RuntimeException exception) {
            throw rejected(
                    "STORY_SUBMISSION_INVALID",
                    field + " must be a UUID.",
                    PublishingSubmissionException.Kind.INVALID
            );
        }
    }

    private static PublishingSubmissionException rejected(
            String code,
            String message,
            PublishingSubmissionException.Kind kind
    ) {
        return new PublishingSubmissionException(code, message, kind);
    }

    public record StorySubmitted(
            String reviewId,
            String storyId,
            String submittedRevision,
            int chapterCount
    ) {
    }
}
