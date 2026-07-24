package com.storyplatform.moderation.infrastructure.persistence;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = MongoModerationChapterDocument.COLLECTION)
public record MongoModerationChapterDocument(
        @Id String id,
        String storyId,
        String teamId,
        String workflowStatus,
        String currentRevision,
        long version
) {
    public static final String COLLECTION = "chapters";
}
