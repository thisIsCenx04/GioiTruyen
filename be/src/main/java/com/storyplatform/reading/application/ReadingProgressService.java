package com.storyplatform.reading.application;

import com.storyplatform.reading.application.port.ReadingProgressRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class ReadingProgressService
        implements ReadingProgressOperations {

    private static final Duration MAXIMUM_FUTURE_SKEW =
            Duration.ofMinutes(5);
    private final ReadingProgressRepository repository;
    private final Clock clock;

    public ReadingProgressService(
            ReadingProgressRepository repository,
            Clock clock
    ) {
        this.repository = Objects.requireNonNull(repository);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public ProgressView get(String userId, String storyId) {
        return repository.find(uuid(userId), uuid(storyId))
                .map(ReadingProgressRepository.StoredProgress::progress)
                .orElseThrow(() -> rejected(
                        "READING_PROGRESS_NOT_FOUND",
                        "Reading progress does not exist.",
                        ReadingProgressException.Kind.NOT_FOUND
                ));
    }

    @Override
    public ProgressView synchronize(
            String userId,
            String storyId,
            Long expectedVersion,
            SyncCommand command
    ) {
        String user = uuid(userId);
        String story = uuid(storyId);
        if (command == null
                || !Double.isFinite(command.position())
                || command.position() < 0
                || command.position() > 100
                || command.deviceUpdatedAt() == null
                || command.deviceUpdatedAt().isAfter(
                clock.instant().plus(MAXIMUM_FUTURE_SKEW)
        )) {
            throw rejected(
                    "READING_PROGRESS_INVALID",
                    "Reading position or device timestamp is invalid.",
                    ReadingProgressException.Kind.INVALID
            );
        }
        String chapter = uuid(command.chapterId());
        if (!repository.chapterIsPublished(story, chapter)) {
            throw rejected(
                    "READING_CHAPTER_NOT_FOUND",
                    "Published chapter does not belong to this story.",
                    ReadingProgressException.Kind.NOT_FOUND
            );
        }
        Instant now = clock.instant();
        var current = repository.find(user, story);
        if (current.isEmpty()) {
            if (expectedVersion != null) {
                throw conflict();
            }
            ProgressView created = view(
                    story, chapter, command, now, 1
            );
            if (!repository.create(user, created)) {
                throw conflict();
            }
            return created;
        }
        ProgressView existing = current.orElseThrow().progress();
        if (!command.deviceUpdatedAt().isAfter(
                existing.deviceUpdatedAt()
        )) {
            return existing;
        }
        if (expectedVersion == null
                || expectedVersion != existing.version()) {
            throw conflict();
        }
        ProgressView updated = view(
                story,
                chapter,
                command,
                now,
                existing.version() + 1
        );
        if (!repository.update(
                user,
                updated,
                expectedVersion,
                existing.deviceUpdatedAt()
        )) {
            throw conflict();
        }
        return updated;
    }

    private static ProgressView view(
            String story,
            String chapter,
            SyncCommand command,
            Instant now,
            long version
    ) {
        return new ProgressView(
                story,
                chapter,
                command.position(),
                command.deviceUpdatedAt(),
                now,
                version
        );
    }

    private static String uuid(String value) {
        try {
            return UUID.fromString(value).toString();
        } catch (RuntimeException exception) {
            throw rejected(
                    "READING_PROGRESS_INVALID",
                    "A reading progress identifier is invalid.",
                    ReadingProgressException.Kind.INVALID
            );
        }
    }

    private static ReadingProgressException conflict() {
        return rejected(
                "READING_PROGRESS_CONFLICT",
                "Reading progress changed on another device.",
                ReadingProgressException.Kind.CONFLICT
        );
    }

    private static ReadingProgressException rejected(
            String code,
            String detail,
            ReadingProgressException.Kind kind
    ) {
        return new ReadingProgressException(code, detail, kind);
    }
}
