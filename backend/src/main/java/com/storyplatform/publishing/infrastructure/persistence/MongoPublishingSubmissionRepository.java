package com.storyplatform.publishing.infrastructure.persistence;

import com.storyplatform.publishing.application.port
        .PublishingSubmissionRepository;
import com.storyplatform.publishing.domain.ChapterDraft;
import com.storyplatform.publishing.domain.PublishingReview;
import com.storyplatform.publishing.domain.StoryDraft;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class MongoPublishingSubmissionRepository
        implements PublishingSubmissionRepository {

    private final MongoTemplate mongo;

    public MongoPublishingSubmissionRepository(MongoTemplate mongo) {
        this.mongo = Objects.requireNonNull(mongo, "mongo");
    }

    @Override
    public Optional<PublishingReview> findReplay(
            String teamId,
            String idempotencyKey
    ) {
        MongoPublishingReviewDocument review = mongo.findOne(
                Query.query(Criteria.where("teamId").is(teamId)
                        .and("idempotencyKey").is(idempotencyKey)),
                MongoPublishingReviewDocument.class
        );
        return Optional.ofNullable(review)
                .map(MongoPublishingReviewDocument::toDomain);
    }

    @Override
    public Optional<SubmissionCandidate> findCandidate(
            String teamId,
            String storyId
    ) {
        MongoStoryDraftDocument story = mongo.findOne(
                Query.query(Criteria.where("_id").is(storyId)
                        .and("teamId").is(teamId)
                        .and("workflowStatus").is(
                                StoryDraft.WorkflowStatus.DRAFT
                        )),
                MongoStoryDraftDocument.class
        );
        if (story == null) {
            return Optional.empty();
        }
        List<PublishingReview.ChapterRevisionRef> chapters = mongo.find(
                        Query.query(Criteria.where("storyId").is(storyId)
                                        .and("teamId").is(teamId)
                                        .and("workflowStatus").is(
                                                ChapterDraft.WorkflowStatus
                                                        .DRAFT
                                        ))
                                .with(Sort.by(
                                        Sort.Direction.ASC,
                                        "number"
                                )),
                        MongoChapterDraftDocument.class
                ).stream()
                .map(value -> new PublishingReview.ChapterRevisionRef(
                        value.id(),
                        value.currentRevision(),
                        value.number()
                ))
                .toList();
        return Optional.of(new SubmissionCandidate(
                story.id(),
                story.teamId(),
                story.currentRevision(),
                story.version(),
                chapters
        ));
    }

    @Override
    public boolean submit(
            SubmissionCandidate candidate,
            PublishingReview review
    ) {
        var result = mongo.updateFirst(
                Query.query(new Criteria().andOperator(
                        Criteria.where("_id").is(candidate.storyId()),
                        Criteria.where("teamId").is(candidate.teamId()),
                        Criteria.where("workflowStatus").is(
                                StoryDraft.WorkflowStatus.DRAFT
                        ),
                        Criteria.where("currentRevision").is(
                                candidate.storyRevision()
                        ),
                        Criteria.where("version").is(
                                candidate.storyVersion()
                        )
                )),
                new Update()
                        .set(
                                "workflowStatus",
                                StoryDraft.WorkflowStatus.IN_REVIEW
                        )
                        .set("updatedAt", review.submittedAt())
                        .inc("version", 1),
                MongoStoryDraftDocument.class
        );
        if (result.getModifiedCount() != 1) {
            return false;
        }
        mongo.insert(MongoPublishingReviewDocument.from(review));
        return true;
    }
}
