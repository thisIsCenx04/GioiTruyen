package com.storyplatform.publishing.application;

import com.storyplatform.publishing.domain.StoryDraft;

import java.time.Instant;
import java.util.List;

public interface StoryDraftOperations {

    DraftView create(
            String actorId,
            String teamId,
            String idempotencyKey,
            CreateCommand command
    );

    DraftView update(
            String actorId,
            String teamId,
            String storyId,
            long expectedVersion,
            UpdateCommand command
    );

    record CreateCommand(
            String title,
            String synopsis,
            StoryDraft.Origin origin,
            String language,
            List<String> categoryIds,
            String coverAssetId
    ) {
        public CreateCommand {
            categoryIds = categoryIds == null
                    ? null
                    : List.copyOf(categoryIds);
        }
    }

    record UpdateCommand(
            String title,
            String synopsis,
            List<String> categoryIds,
            String coverAssetId,
            StoryDraft.CompletionStatus completionStatus
    ) {
        public UpdateCommand {
            categoryIds = categoryIds == null
                    ? null
                    : List.copyOf(categoryIds);
        }
    }

    record DraftView(
            String id,
            String teamId,
            String slug,
            String title,
            String synopsis,
            StoryDraft.Origin origin,
            String language,
            List<String> categoryIds,
            String coverAssetId,
            StoryDraft.CompletionStatus completionStatus,
            StoryDraft.WorkflowStatus workflowStatus,
            String currentRevision,
            long revisionNo,
            long version,
            Instant createdAt,
            Instant updatedAt
    ) {
        public DraftView {
            categoryIds = List.copyOf(categoryIds);
        }
    }
}
