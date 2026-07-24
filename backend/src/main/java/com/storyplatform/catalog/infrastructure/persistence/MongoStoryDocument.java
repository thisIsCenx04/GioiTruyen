package com.storyplatform.catalog.infrastructure.persistence;

import com.storyplatform.catalog.domain.Story;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document(collection = MongoStoryDocument.COLLECTION)
public record MongoStoryDocument(
        @Id String id,
        String teamId,
        String slug,
        String title,
        List<String> aliases,
        String synopsis,
        List<String> categoryIds,
        Story.Origin origin,
        String language,
        Story.CompletionStatus completionStatus,
        Story.WorkflowStatus workflowStatus,
        String currentRevision,
        String coverAssetId,
        Instant publishedAt,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    public static final String COLLECTION = "stories";
}
