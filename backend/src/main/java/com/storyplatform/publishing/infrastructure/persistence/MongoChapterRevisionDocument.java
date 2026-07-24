package com.storyplatform.publishing.infrastructure.persistence;

import com.storyplatform.publishing.domain.ChapterRevision;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = MongoChapterRevisionDocument.COLLECTION)
public record MongoChapterRevisionDocument(
        @Id String id,
        String chapterId,
        long revisionNo,
        String contentHtml,
        String plainText,
        String checksum,
        String createdBy,
        Instant createdAt
) {
    public static final String COLLECTION = "chapter_revisions";

    public static MongoChapterRevisionDocument from(
            ChapterRevision revision
    ) {
        return new MongoChapterRevisionDocument(
                revision.id(),
                revision.chapterId(),
                revision.revisionNo(),
                revision.contentHtml(),
                revision.plainText(),
                revision.checksum(),
                revision.createdBy(),
                revision.createdAt()
        );
    }
}
