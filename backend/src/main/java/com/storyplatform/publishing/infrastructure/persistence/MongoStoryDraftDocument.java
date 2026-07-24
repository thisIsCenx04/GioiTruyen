package com.storyplatform.publishing.infrastructure.persistence;

import com.storyplatform.publishing.domain.StoryDraft;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document(collection = MongoStoryDraftDocument.COLLECTION)
public record MongoStoryDraftDocument(
        @Id String id,
        String teamId,
        String slug,
        String title,
        List<String> aliases,
        String synopsis,
        List<String> categoryIds,
        StoryDraft.Origin origin,
        String language,
        StoryDraft.CompletionStatus completionStatus,
        StoryDraft.WorkflowStatus workflowStatus,
        String currentRevision,
        String coverAssetId,
        Instant publishedAt,
        Instant createdAt,
        Instant updatedAt,
        long version,
        String createdBy,
        String idempotencyKey,
        String idempotencyFingerprint
) {
    public static final String COLLECTION = "stories";
}
