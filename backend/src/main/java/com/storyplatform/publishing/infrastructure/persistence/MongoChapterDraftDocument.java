package com.storyplatform.publishing.infrastructure.persistence;

import com.storyplatform.publishing.domain.ChapterDraft;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = MongoChapterDraftDocument.COLLECTION)
public record MongoChapterDraftDocument(
        @Id String id,
        String storyId,
        String teamId,
        int number,
        String slug,
        String title,
        ChapterDraft.WorkflowStatus workflowStatus,
        String currentRevision,
        long currentRevisionNo,
        Instant scheduledAt,
        Instant publishedAt,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    public static final String COLLECTION = "chapters";

    public static MongoChapterDraftDocument from(ChapterDraft chapter) {
        return new MongoChapterDraftDocument(
                chapter.id(),
                chapter.storyId(),
                chapter.teamId(),
                chapter.number(),
                chapter.slug(),
                chapter.title(),
                chapter.workflowStatus(),
                chapter.currentRevision(),
                chapter.currentRevisionNo(),
                null,
                null,
                chapter.createdAt(),
                chapter.updatedAt(),
                chapter.version()
        );
    }
}
