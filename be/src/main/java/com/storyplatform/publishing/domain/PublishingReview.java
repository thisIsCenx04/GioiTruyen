package com.storyplatform.publishing.domain;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record PublishingReview(
        String id,
        TargetType targetType,
        String targetId,
        String teamId,
        String submittedRevision,
        long submittedVersion,
        List<ChapterRevisionRef> chapterRevisions,
        State state,
        String submittedBy,
        Instant submittedAt,
        long version,
        String idempotencyKey,
        String idempotencyFingerprint
) {
    public PublishingReview {
        id = uuid(id, "id");
        targetId = uuid(targetId, "targetId");
        teamId = uuid(teamId, "teamId");
        submittedRevision = uuid(
                submittedRevision,
                "submittedRevision"
        );
        submittedBy = uuid(submittedBy, "submittedBy");
        Objects.requireNonNull(targetType, "targetType");
        Objects.requireNonNull(state, "state");
        submittedAt = Objects.requireNonNull(
                submittedAt,
                "submittedAt"
        );
        chapterRevisions = List.copyOf(chapterRevisions);
        if (targetType != TargetType.STORY
                || submittedVersion < 1
                || version != 1
                || chapterRevisions.isEmpty()
                || idempotencyKey == null
                || idempotencyKey.isBlank()
                || idempotencyFingerprint == null
                || !idempotencyFingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    "publishing review is invalid"
            );
        }
        var chapterIds = new HashSet<String>();
        var chapterNumbers = new HashSet<Integer>();
        for (ChapterRevisionRef chapter : chapterRevisions) {
            if (!chapterIds.add(chapter.chapterId())
                    || !chapterNumbers.add(chapter.number())) {
                throw new IllegalArgumentException(
                        "chapter revision references must be unique"
                );
            }
        }
    }

    public record ChapterRevisionRef(
            String chapterId,
            String revisionId,
            int number
    ) {
        public ChapterRevisionRef {
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
        AUTOMATED_CHECK_PENDING,
        AUTOMATED_CHECK_RUNNING,
        OPEN,
        CLAIMED,
        APPROVED,
        CHANGES_REQUESTED,
        REJECTED
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
