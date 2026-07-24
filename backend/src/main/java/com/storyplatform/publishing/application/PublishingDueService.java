package com.storyplatform.publishing.application;

import com.storyplatform.publishing.application.port.PublishingDueRepository;
import com.storyplatform.publishing.domain.PublishingSchedule;
import com.storyplatform.shared.events.IntegrationEvent;
import com.storyplatform.shared.events.persistence.OutboxAppender;
import com.storyplatform.teams.application.contract.TeamStatusDirectory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class PublishingDueService implements PublishingDueOperations {

    public static final String EVENT_TYPE =
            "publishing.chapter.published";

    private final PublishingDueRepository repository;
    private final TeamStatusDirectory teams;
    private final OutboxAppender outbox;
    private final Supplier<String> identifiers;
    private final Clock clock;
    private final Duration leaseDuration;
    private final Duration inactiveRetryDelay;

    public PublishingDueService(
            PublishingDueRepository repository,
            TeamStatusDirectory teams,
            OutboxAppender outbox,
            Supplier<String> identifiers,
            Clock clock,
            Duration leaseDuration,
            Duration inactiveRetryDelay
    ) {
        this.repository = Objects.requireNonNull(
                repository,
                "repository"
        );
        this.teams = Objects.requireNonNull(teams, "teams");
        this.outbox = Objects.requireNonNull(outbox, "outbox");
        this.identifiers = Objects.requireNonNull(
                identifiers,
                "identifiers"
        );
        this.clock = Objects.requireNonNull(clock, "clock");
        this.leaseDuration = positive(
                leaseDuration,
                "leaseDuration"
        );
        this.inactiveRetryDelay = positive(
                inactiveRetryDelay,
                "inactiveRetryDelay"
        );
    }

    @Override
    public boolean processNext(String workerId) {
        String worker = worker(workerId);
        Instant claimedAt = clock.instant();
        var candidate = repository.claim(
                worker,
                claimedAt,
                claimedAt.plus(leaseDuration)
        );
        if (candidate.isEmpty()) {
            return false;
        }
        PublishingSchedule schedule = candidate.orElseThrow();
        if (!teams.isActive(schedule.teamId())) {
            if (!repository.defer(
                    schedule,
                    worker,
                    claimedAt.plus(inactiveRetryDelay),
                    clock.instant()
            )) {
                throw stale();
            }
            return true;
        }
        Instant publishedAt = clock.instant();
        if (!repository.publish(schedule, worker, publishedAt)) {
            throw stale();
        }
        for (PublishingSchedule.FrozenChapterRevision chapter
                : schedule.chapterRevisions()) {
            outbox.append(new IntegrationEvent(
                    UUID.fromString(identifiers.get()),
                    EVENT_TYPE,
                    1,
                    publishedAt,
                    schedule.id(),
                    "chapter",
                    chapter.chapterId(),
                    worker,
                    schedule.teamId(),
                    new ChapterPublishedPayload(
                            schedule.teamId(),
                            schedule.targetId(),
                            chapter.chapterId(),
                            chapter.revisionId(),
                            publishedAt
                    )
            ));
        }
        return true;
    }

    private static Duration positive(Duration value, String field) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(
                    field + " must be positive"
            );
        }
        return value;
    }

    private static String worker(String value) {
        try {
            return UUID.fromString(value).toString();
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    "workerId must be a UUID",
                    exception
            );
        }
    }

    private static PublishingDueException stale() {
        return new PublishingDueException(
                "PUBLISHING_DUE_STALE",
                "The due schedule or one of its pinned revisions changed."
        );
    }

    public record ChapterPublishedPayload(
            String teamId,
            String storyId,
            String chapterId,
            String revision,
            Instant publishedAt
    ) {
    }
}
