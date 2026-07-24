package com.storyplatform.publishing.infrastructure.persistence;

import com.storyplatform.publishing.application.PublishingPrecheckEngine;
import com.storyplatform.publishing.application.port
        .PublishingPrecheckRepository;
import com.storyplatform.publishing.domain.PublishingReview;
import org.bson.Document;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class MongoPublishingPrecheckRepository
        implements PublishingPrecheckRepository {

    private static final String MEDIA_COLLECTION = "media_assets";

    private final MongoTemplate mongo;

    public MongoPublishingPrecheckRepository(MongoTemplate mongo) {
        this.mongo = Objects.requireNonNull(mongo, "mongo");
    }

    @Override
    public Optional<ClaimedReview> claim(
            String workerId,
            Instant now,
            Instant leaseUntil
    ) {
        Criteria available = new Criteria().orOperator(
                Criteria.where("state").is(
                        PublishingReview.State.AUTOMATED_CHECK_PENDING
                ),
                new Criteria().andOperator(
                        Criteria.where("state").is(
                                PublishingReview.State
                                        .AUTOMATED_CHECK_RUNNING
                        ),
                        Criteria.where("leaseUntil").lt(now)
                )
        );
        Query query = Query.query(available);
        query.with(org.springframework.data.domain.Sort.by(
                "submittedAt",
                "_id"
        ));
        MongoPublishingReviewDocument claimed = mongo.findAndModify(
                query,
                new Update()
                        .set(
                                "state",
                                PublishingReview.State
                                        .AUTOMATED_CHECK_RUNNING
                        )
                        .set("leaseOwner", workerId)
                        .set("leaseUntil", leaseUntil)
                        .set("updatedAt", now)
                        .inc("version", 1),
                FindAndModifyOptions.options().returnNew(true),
                MongoPublishingReviewDocument.class
        );
        return Optional.ofNullable(claimed).map(this::claimed);
    }

    @Override
    public Optional<ReviewEvidence> loadEvidence(ClaimedReview review) {
        MongoStoryRevisionDocument story = mongo.findOne(
                Query.query(Criteria.where("_id").is(
                                review.storyRevision()
                        )
                        .and("storyId").is(review.storyId())),
                MongoStoryRevisionDocument.class
        );
        if (story == null) {
            return Optional.empty();
        }
        List<String> revisionIds = review.chapters().stream()
                .map(FrozenChapter::revisionId)
                .toList();
        List<MongoChapterRevisionDocument> documents = mongo.find(
                Query.query(Criteria.where("_id").in(revisionIds)),
                MongoChapterRevisionDocument.class
        );
        Map<String, MongoChapterRevisionDocument> byId = documents.stream()
                .collect(Collectors.toMap(
                        MongoChapterRevisionDocument::id,
                        Function.identity()
                ));
        List<ChapterEvidence> chapters = review.chapters().stream()
                .map(reference -> evidence(reference, byId))
                .filter(Objects::nonNull)
                .toList();
        String coverAssetId = story.snapshot().coverAssetId();
        MediaEvidence cover = coverAssetId == null
                ? null
                : cover(coverAssetId);
        return Optional.of(new ReviewEvidence(
                review,
                coverAssetId,
                cover,
                chapters
        ));
    }

    @Override
    public boolean complete(
            ClaimedReview review,
            String workerId,
            List<PublishingPrecheckEngine.CheckResult> checks,
            boolean manualFallback,
            Instant completedAt
    ) {
        var result = mongo.updateFirst(
                Query.query(Criteria.where("_id").is(review.reviewId())
                        .and("state").is(
                                PublishingReview.State
                                        .AUTOMATED_CHECK_RUNNING
                        )
                        .and("leaseOwner").is(workerId)),
                new Update()
                        .set("state", PublishingReview.State.OPEN)
                        .set("checks", checks)
                        .set("manualFallback", manualFallback)
                        .set("checkedAt", completedAt)
                        .set("updatedAt", completedAt)
                        .unset("leaseOwner")
                        .unset("leaseUntil")
                        .inc("version", 1),
                MongoPublishingReviewDocument.class
        );
        return result.getModifiedCount() == 1;
    }

    private ClaimedReview claimed(
            MongoPublishingReviewDocument document
    ) {
        List<FrozenChapter> chapters = document.chapterRevisions()
                .stream()
                .map(value -> new FrozenChapter(
                        value.chapterId(),
                        value.revisionId(),
                        value.number()
                ))
                .toList();
        return new ClaimedReview(
                document.id(),
                document.teamId(),
                document.targetId(),
                document.submittedRevision(),
                chapters,
                document.version(),
                document.leaseUntil()
        );
    }

    private static ChapterEvidence evidence(
            FrozenChapter reference,
            Map<String, MongoChapterRevisionDocument> documents
    ) {
        MongoChapterRevisionDocument revision = documents.get(
                reference.revisionId()
        );
        if (revision == null
                || !reference.chapterId().equals(revision.chapterId())) {
            return null;
        }
        return new ChapterEvidence(
                revision.chapterId(),
                revision.contentHtml(),
                revision.plainText(),
                revision.checksum()
        );
    }

    private MediaEvidence cover(String assetId) {
        Document document = mongo.findOne(
                Query.query(Criteria.where("_id").is(assetId)),
                Document.class,
                MEDIA_COLLECTION
        );
        if (document == null) {
            return null;
        }
        return new MediaEvidence(
                document.getString("ownerType"),
                document.getString("ownerId"),
                document.getString("purpose"),
                document.getString("state"),
                document.getString("moderationState")
        );
    }
}
