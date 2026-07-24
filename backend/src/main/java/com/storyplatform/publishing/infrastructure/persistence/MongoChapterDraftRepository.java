package com.storyplatform.publishing.infrastructure.persistence;

import com.storyplatform.publishing.application.port.ChapterDraftRepository;
import com.storyplatform.publishing.domain.ChapterDraft;
import com.storyplatform.publishing.domain.ChapterRevision;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.Objects;
import java.util.Optional;

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

    @Override
    public Optional<StoredChapter> findOwned(
            String teamId,
            String storyId,
            String chapterId
    ) {
        MongoChapterDraftDocument document = mongo.findOne(
                Query.query(new Criteria().andOperator(
                        Criteria.where("_id").is(chapterId),
                        Criteria.where("storyId").is(storyId),
                        Criteria.where("teamId").is(teamId),
                        Criteria.where("workflowStatus").is(
                                ChapterDraft.WorkflowStatus.DRAFT
                        )
                )),
                MongoChapterDraftDocument.class
        );
        return Optional.ofNullable(document)
                .map(value -> new StoredChapter(toDomain(value)));
    }

    @Override
    public boolean update(
            ChapterDraft chapter,
            ChapterRevision revision,
            long expectedVersion
    ) {
        var result = mongo.updateFirst(
                Query.query(new Criteria().andOperator(
                        Criteria.where("_id").is(chapter.id()),
                        Criteria.where("storyId").is(chapter.storyId()),
                        Criteria.where("teamId").is(chapter.teamId()),
                        Criteria.where("workflowStatus").is(
                                ChapterDraft.WorkflowStatus.DRAFT
                        ),
                        Criteria.where("version").is(expectedVersion)
                )),
                new Update()
                        .set("title", chapter.title())
                        .set("currentRevision", chapter.currentRevision())
                        .set(
                                "currentRevisionNo",
                                chapter.currentRevisionNo()
                        )
                        .set("updatedAt", chapter.updatedAt())
                        .set("version", chapter.version()),
                MongoChapterDraftDocument.class
        );
        if (result.getModifiedCount() != 1) {
            return false;
        }
        mongo.insert(MongoChapterRevisionDocument.from(revision));
        return true;
    }

    private static ChapterDraft toDomain(
            MongoChapterDraftDocument value
    ) {
        return new ChapterDraft(
                value.id(),
                value.storyId(),
                value.teamId(),
                value.number(),
                value.slug(),
                value.title(),
                value.workflowStatus(),
                value.currentRevision(),
                value.currentRevisionNo(),
                value.createdAt(),
                value.updatedAt(),
                value.version()
        );
    }
}
