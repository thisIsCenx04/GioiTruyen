package com.storyplatform.publishing.infrastructure.persistence;

import com.storyplatform.publishing.domain.StoryRevision;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = MongoStoryRevisionDocument.COLLECTION)
public record MongoStoryRevisionDocument(
        @Id String id,
        String storyId,
        long revisionNo,
        StoryRevision.Snapshot snapshot,
        String createdBy,
        String checksum,
        Instant createdAt
) {
    public static final String COLLECTION = "story_revisions";

    static MongoStoryRevisionDocument from(StoryRevision revision) {
        return new MongoStoryRevisionDocument(
                revision.id(),
                revision.storyId(),
                revision.revisionNo(),
                revision.snapshot(),
                revision.createdBy(),
                revision.checksum(),
                revision.createdAt()
        );
    }
}
