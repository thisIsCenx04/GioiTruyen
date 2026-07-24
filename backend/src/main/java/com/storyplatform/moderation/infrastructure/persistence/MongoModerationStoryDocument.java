package com.storyplatform.moderation.infrastructure.persistence;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = MongoModerationStoryDocument.COLLECTION)
public record MongoModerationStoryDocument(
        @Id String id,
        String teamId,
        String workflowStatus,
        String currentRevision,
        long version
) {
    public static final String COLLECTION = "stories";
}
