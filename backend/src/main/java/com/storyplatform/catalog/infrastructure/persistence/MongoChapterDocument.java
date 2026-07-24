package com.storyplatform.catalog.infrastructure.persistence;

import com.storyplatform.catalog.domain.Chapter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = MongoChapterDocument.COLLECTION)
public record MongoChapterDocument(
        @Id String id,
        String storyId,
        String teamId,
        int number,
        String slug,
        String title,
        Chapter.WorkflowStatus workflowStatus,
        String currentRevision,
        Instant scheduledAt,
        Instant publishedAt,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    public static final String COLLECTION = "chapters";
}
