package com.storyplatform.publishing.domain;

import java.time.Instant;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record PublishingSchedule(
        String id,
        TargetType targetType,
        String targetId,
        String teamId,
        String revision,
        List<FrozenChapterRevision> chapterRevisions,
        State state,
        Instant publishAt,
        String timeZone,
        String createdBy,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    public PublishingSchedule {
        id = uuid(id, "id");
        targetId = uuid(targetId, "targetId");
        teamId = uuid(teamId, "teamId");
        revision = uuid(revision, "revision");
        createdBy = uuid(createdBy, "createdBy");
        Objects.requireNonNull(targetType, "targetType");
        Objects.requireNonNull(state, "state");
        publishAt = Objects.requireNonNull(publishAt, "publishAt");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        ZoneId.of(timeZone);
        chapterRevisions = List.copyOf(chapterRevisions);
        var chapterIds = new HashSet<String>();
        if (targetType != TargetType.STORY
                || chapterRevisions.isEmpty()
                || version < 1
                || updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException(
                    "publishing schedule is invalid"
            );
        }
        for (FrozenChapterRevision chapter : chapterRevisions) {
            if (!chapterIds.add(chapter.chapterId())) {
                throw new IllegalArgumentException(
                        "scheduled chapter revisions must be unique"
                );
            }
        }
    }

    public record FrozenChapterRevision(
            String chapterId,
            String revisionId,
            int number
    ) {
        public FrozenChapterRevision {
            chapterId = uuid(chapterId, "chapterId");
            revisionId = uuid(revisionId, "revisionId");
            if (number < 1) {
                throw new IllegalArgumentException(
                        "chapter number must be positive"
                );
            }
        }
    }

    public enum TargetType {
        STORY
    }

    public enum State {
        SCHEDULED,
        CANCELLED
    }

    private static String uuid(String value, String field) {
        try {
            return UUID.fromString(value).toString();
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    field + " must be a UUID",
                    exception
            );
        }
    }
}
