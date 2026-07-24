package com.storyplatform.publishing.infrastructure.persistence;

import com.storyplatform.publishing.application.port.StoryDraftRepository;
import com.storyplatform.publishing.domain.StoryDraft;
import com.storyplatform.publishing.domain.StoryRevision;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.Objects;
import java.util.Optional;

public final class MongoStoryDraftRepository
        implements StoryDraftRepository {

    private final MongoTemplate mongo;

    public MongoStoryDraftRepository(MongoTemplate mongo) {
        this.mongo = Objects.requireNonNull(mongo, "mongo");
    }

    @Override
    public Optional<StoredDraft> findReplay(
            String teamId,
            String idempotencyKey
    ) {
        MongoStoryDraftDocument document = mongo.findOne(
                Query.query(Criteria.where("teamId").is(teamId)
                        .and("idempotencyKey").is(idempotencyKey)),
                MongoStoryDraftDocument.class
        );
        return Optional.ofNullable(document).map(value ->
                new StoredDraft(
                        story(value),
                        1,
                        value.idempotencyFingerprint()
                ));
    }

    @Override
    public void insert(
            StoryDraft story,
            StoryRevision revision,
            String createdBy,
            String idempotencyKey,
            String idempotencyFingerprint
    ) {
        mongo.insert(new MongoStoryDraftDocument(
                story.id(),
                story.teamId(),
                story.slug(),
                story.title(),
                java.util.List.of(),
                story.synopsis(),
                story.categoryIds(),
                story.origin(),
                story.language(),
                story.completionStatus(),
                story.workflowStatus(),
                story.currentRevision(),
                story.coverAssetId(),
                null,
                story.createdAt(),
                story.updatedAt(),
                story.version(),
                createdBy,
                idempotencyKey,
                idempotencyFingerprint
        ));
        mongo.insert(MongoStoryRevisionDocument.from(revision));
    }

    private static StoryDraft story(MongoStoryDraftDocument value) {
        return new StoryDraft(
                value.id(),
                value.teamId(),
                value.slug(),
                value.title(),
                value.synopsis(),
                value.categoryIds(),
                value.origin(),
                value.language(),
                value.completionStatus(),
                value.workflowStatus(),
                value.currentRevision(),
                value.coverAssetId(),
                value.createdAt(),
                value.updatedAt(),
                value.version()
        );
    }
}
