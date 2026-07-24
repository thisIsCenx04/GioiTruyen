package com.storyplatform.publishing.infrastructure.persistence;

import com.storyplatform.publishing.application.port.ChapterDraftRepository;
import com.storyplatform.publishing.domain.ChapterDraft;
import com.storyplatform.publishing.domain.ChapterRevision;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.Objects;

public final class MongoChapterDraftRepository
        implements ChapterDraftRepository {

    private final MongoTemplate mongo;

    public MongoChapterDraftRepository(MongoTemplate mongo) {
        this.mongo = Objects.requireNonNull(mongo, "mongo");
    }

    @Override
    public boolean numberExists(String storyId, int number) {
        return mongo.exists(
                Query.query(Criteria.where("storyId").is(storyId)
                        .and("number").is(number)),
                MongoChapterDraftDocument.class
        );
    }

    @Override
    public void insert(
            ChapterDraft chapter,
            ChapterRevision revision
    ) {
        mongo.insert(MongoChapterDraftDocument.from(chapter));
        mongo.insert(MongoChapterRevisionDocument.from(revision));
    }
}
