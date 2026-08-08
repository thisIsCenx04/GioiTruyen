package com.storyplatform.publishing.application;

import java.time.Instant;

public interface ChapterDraftOperations {

    ChapterView create(
            String actorId,
            String teamId,
            String storyId,
            CreateCommand command
    );

    ChapterView update(
            String actorId,
            String teamId,
            String storyId,
            String chapterId,
            long expectedVersion,
            UpdateCommand command
    );

    record CreateCommand(int number, String title, String contentHtml) {
    }

    record UpdateCommand(String title, String contentHtml) {
    }

    record ChapterView(
            String id,
            String storyId,
            String teamId,
            int number,
            String slug,
            String title,
            String workflowStatus,
            String currentRevision,
            long revisionNo,
            long version,
            int wordCount,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
