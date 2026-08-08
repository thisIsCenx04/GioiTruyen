package com.storyplatform.publishing.application;

import java.time.Instant;
import java.util.List;

public interface PublishingWorkspaceOperations {

    List<StorySummary> stories(String actorId, String teamId);

    StorySummary story(
            String actorId,
            String teamId,
            String storyId
    );

    List<ChapterEditor> chapters(
            String actorId,
            String teamId,
            String storyId
    );

    record StorySummary(
            String id,
            String teamId,
            String slug,
            String title,
            String synopsis,
            String origin,
            String language,
            List<String> categoryIds,
            String coverAssetId,
            String completionStatus,
            String workflowStatus,
            String currentRevision,
            long revisionNo,
            long version,
            Instant updatedAt
    ) {
        public StorySummary {
            categoryIds = List.copyOf(categoryIds);
        }
    }

    record ChapterEditor(
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
            String contentHtml,
            int wordCount,
            Instant updatedAt
    ) {
    }
}
