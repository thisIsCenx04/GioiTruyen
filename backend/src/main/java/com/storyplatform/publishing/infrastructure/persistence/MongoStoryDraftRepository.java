package com.storyplatform.publishing.infrastructure.persistence;

import com.storyplatform.publishing.application.port.StoryDraftRepository;
import com.storyplatform.publishing.domain.StoryDraft;
import com.storyplatform.publishing.domain.StoryRevision;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

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
                        revisionNo(value),
                        value.idempotencyFingerprint()
                ));
    }

    @Override
    public Optional<StoredDraft> findOwned(
            String teamId,
            String storyId
    ) {
        MongoStoryDraftDocument document = mongo.findOne(
                Query.query(Criteria.where("_id").is(storyId)
                        .and("teamId").is(teamId)
                        .and("workflowStatus").in(
                                StoryDraft.WorkflowStatus.DRAFT,
                                StoryDraft.WorkflowStatus.CHANGES_REQUESTED
                        )),
                MongoStoryDraftDocument.class
        );
        return Optional.ofNullable(document).map(value ->
                new StoredDraft(
                        story(value),
                        revisionNo(value),
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
                revision.revisionNo(),
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

    @Override
    public boolean update(
            StoryDraft updated,
            StoryRevision revision,
            long expectedVersion
    ) {
        Query ownedVersion = Query.query(new Criteria().andOperator(
                Criteria.where("_id").is(updated.id()),
                Criteria.where("teamId").is(updated.teamId()),
                Criteria.where("version").is(expectedVersion),
                Criteria.where("workflowStatus").in(
                        StoryDraft.WorkflowStatus.DRAFT,
                        StoryDraft.WorkflowStatus.CHANGES_REQUESTED
                )
        ));
        var result = mongo.updateFirst(
                ownedVersion,
                new Update()
                        .set("title", updated.title())
                        .set("synopsis", updated.synopsis())
                        .set("categoryIds", updated.categoryIds())
                        .set("coverAssetId", updated.coverAssetId())
                        .set(
                                "completionStatus",
                                updated.completionStatus()
                        )
                        .set(
                                "workflowStatus",
                                StoryDraft.WorkflowStatus.DRAFT
                        )
                        .set(
                                "currentRevision",
                                updated.currentRevision()
                        )
                        .set(
                                "currentRevisionNo",
                                revision.revisionNo()
                        )
                        .set("updatedAt", updated.updatedAt())
                        .set("version", updated.version()),
                MongoStoryDraftDocument.class
        );
        if (result.getModifiedCount() != 1) {
            return false;
        }
        mongo.insert(MongoStoryRevisionDocument.from(revision));
        return true;
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

    private static long revisionNo(MongoStoryDraftDocument value) {
        return Math.max(1, value.currentRevisionNo());
    }
}
