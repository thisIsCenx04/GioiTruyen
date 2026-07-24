package com.storyplatform.media.infrastructure.persistence;

import com.storyplatform.media.application.MediaProcessingOperations;
import com.storyplatform.media.domain.MediaOwnerType;
import com.storyplatform.media.domain.UploadPurpose;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class MongoMediaProcessingRepository
        implements MediaProcessingOperations.Repository {

    private final MongoTemplate mongo;

    public MongoMediaProcessingRepository(MongoTemplate mongo) {
        this.mongo = Objects.requireNonNull(mongo, "mongo");
    }

    @Override
    public Optional<MediaProcessingOperations.Candidate> claim(
            String workerId,
            Instant now,
            Instant leaseUntil,
            int maximumAttempts
    ) {
        Criteria availableState = new Criteria().orOperator(
                Criteria.where("state").is("PENDING_MODERATION"),
                new Criteria().andOperator(
                        Criteria.where("state").is("PROCESSING"),
                        Criteria.where("leaseUntil").lt(now)
                )
        );
        Criteria retryReady = new Criteria().orOperator(
                Criteria.where("nextAttemptAt").exists(false),
                Criteria.where("nextAttemptAt").lte(now)
        );
        Criteria attemptsAvailable = new Criteria().orOperator(
                Criteria.where("processingAttempts").exists(false),
                Criteria.where("processingAttempts").lt(maximumAttempts)
        );
        Query query = Query.query(new Criteria().andOperator(
                availableState,
                retryReady,
                attemptsAvailable
        ));
        query.with(org.springframework.data.domain.Sort.by(
                "receivedAt",
                "_id"
        ));
        MongoMediaAssetDocument claimed = mongo.findAndModify(
                query,
                new Update()
                        .set("state", "PROCESSING")
                        .set("leaseOwner", workerId)
                        .set("leaseUntil", leaseUntil)
                        .unset("nextAttemptAt")
                        .inc("processingAttempts", 1)
                        .inc("version", 1),
                FindAndModifyOptions.options().returnNew(true),
                MongoMediaAssetDocument.class
        );
        return Optional.ofNullable(claimed).map(this::candidate);
    }

    @Override
    public boolean complete(
            MediaProcessingOperations.Candidate candidate,
            String workerId,
            MediaProcessingOperations.PublishedAsset published,
            Instant completedAt
    ) {
        var result = mongo.updateFirst(
                ownedLease(candidate.assetId(), workerId),
                new Update()
                        .set("state", "READY")
                        .set("visibility", "PUBLIC")
                        .set("moderationState", "APPROVED")
                        .set(
                                "normalizedPublicId",
                                published.publicId()
                        )
                        .set("normalizedVersion", published.version())
                        .set("normalizedFormat", published.format())
                        .set("normalizedBytes", published.bytes())
                        .set("processedAt", completedAt)
                        .unset("leaseOwner")
                        .unset("leaseUntil")
                        .unset("lastProcessingError")
                        .inc("version", 1),
                MongoMediaAssetDocument.class
        );
        return result.getModifiedCount() == 1;
    }

    @Override
    public void reject(
            MediaProcessingOperations.Candidate candidate,
            String workerId,
            String reason,
            Instant completedAt
    ) {
        mongo.updateFirst(
                ownedLease(candidate.assetId(), workerId),
                new Update()
                        .set("state", "REJECTED")
                        .set("visibility", "PRIVATE")
                        .set("moderationState", "REJECTED")
                        .set("rejectionCode", reason)
                        .set("processedAt", completedAt)
                        .unset("leaseOwner")
                        .unset("leaseUntil")
                        .inc("version", 1),
                MongoMediaAssetDocument.class
        );
    }

    @Override
    public void reschedule(
            MediaProcessingOperations.Candidate candidate,
            String workerId,
            String reason,
            Instant nextAttemptAt,
            boolean exhausted
    ) {
        String state = exhausted
                ? "PROCESSING_FAILED"
                : "PENDING_MODERATION";
        Update update = new Update()
                .set("state", state)
                .set("lastProcessingError", reason)
                .unset("leaseOwner")
                .unset("leaseUntil")
                .inc("version", 1);
        if ("PENDING_MODERATION".equals(state)) {
            update.set("nextAttemptAt", nextAttemptAt);
        } else {
            update.set("moderationState", "FAILED")
                    .set("visibility", "PRIVATE")
                    .unset("nextAttemptAt");
        }
        mongo.updateFirst(
                ownedLease(candidate.assetId(), workerId),
                update,
                MongoMediaAssetDocument.class
        );
    }

    private static Query ownedLease(String assetId, String workerId) {
        return Query.query(Criteria.where("_id").is(assetId)
                .and("state").is("PROCESSING")
                .and("leaseOwner").is(workerId));
    }

    private MediaProcessingOperations.Candidate candidate(
            MongoMediaAssetDocument document
    ) {
        return new MediaProcessingOperations.Candidate(
                document.assetId(),
                document.publicId(),
                document.cloudinaryVersion(),
                document.format(),
                document.declaredSha256(),
                document.bytes(),
                document.width(),
                document.height(),
                MediaOwnerType.valueOf(document.ownerType()),
                document.ownerId(),
                UploadPurpose.valueOf(document.purpose()),
                document.processingAttempts(),
                document.leaseUntil()
        );
    }
}
