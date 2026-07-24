package com.storyplatform.publishing.infrastructure.persistence;

import com.storyplatform.publishing.application.port
        .PublishingPropagationRepository;
import com.storyplatform.publishing.infrastructure
        .PublishingPropagationHandler;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class MongoPublishingPropagationRepository
        implements PublishingPropagationRepository {

    private final MongoTemplate mongo;

    public MongoPublishingPropagationRepository(MongoTemplate mongo) {
        this.mongo = Objects.requireNonNull(mongo);
    }

    @Override
    public Optional<EdgeTask> claimEdge(
            String workerId,
            Instant now,
            Instant leaseUntil
    ) {
        Criteria lease = new Criteria().orOperator(
                Criteria.where("leaseUntil").exists(false),
                Criteria.where("leaseUntil").is(null),
                Criteria.where("leaseUntil").lte(now)
        );
        Query query = Query.query(new Criteria().andOperator(
                Criteria.where("channel").is("EDGE"),
                Criteria.where("state").in("PENDING", "RUNNING"),
                Criteria.where("availableAt").lte(now),
                lease
        )).with(Sort.by(
                Sort.Order.asc("availableAt"),
                Sort.Order.asc("_id")
        ));
        Document claimed = mongo.findAndModify(
                query,
                new Update()
                        .set("state", "RUNNING")
                        .set("leaseOwner", workerId)
                        .set("leaseUntil", leaseUntil)
                        .set("updatedAt", now)
                        .inc("attempts", 1),
                FindAndModifyOptions.options().returnNew(true),
                Document.class,
                PublishingPropagationHandler.COLLECTION
        );
        if (claimed == null) {
            return Optional.empty();
        }
        List<?> rawTargets = claimed.getList(
                "targets",
                Object.class,
                List.of()
        );
        List<String> targets = rawTargets.stream()
                .map(String::valueOf)
                .toList();
        return Optional.of(new EdgeTask(
                claimed.getString("_id"),
                claimed.getString("eventId"),
                targets,
                claimed.getInteger("attempts", 1)
        ));
    }

    @Override
    public boolean complete(
            EdgeTask task,
            String workerId,
            Instant completedAt
    ) {
        var result = mongo.updateFirst(
                Query.query(new Criteria().andOperator(
                        Criteria.where("_id").is(task.id()),
                        Criteria.where("state").is("RUNNING"),
                        Criteria.where("leaseOwner").is(workerId),
                        Criteria.where("attempts").is(task.attempts())
                )),
                new Update()
                        .set("state", "COMPLETED")
                        .set("completedAt", completedAt)
                        .set("updatedAt", completedAt)
                        .unset("leaseOwner")
                        .unset("leaseUntil"),
                PublishingPropagationHandler.COLLECTION
        );
        return result.getModifiedCount() == 1;
    }

    @Override
    public boolean fail(
            EdgeTask task,
            String workerId,
            Instant failedAt,
            Instant availableAt,
            String errorCode,
            int maximumAttempts
    ) {
        boolean exhausted = task.attempts() >= maximumAttempts;
        var result = mongo.updateFirst(
                Query.query(new Criteria().andOperator(
                        Criteria.where("_id").is(task.id()),
                        Criteria.where("state").is("RUNNING"),
                        Criteria.where("leaseOwner").is(workerId),
                        Criteria.where("attempts").is(task.attempts())
                )),
                new Update()
                        .set("state", exhausted ? "DEAD" : "PENDING")
                        .set("lastErrorCode", errorCode)
                        .set("lastFailedAt", failedAt)
                        .set("availableAt", availableAt)
                        .set("updatedAt", failedAt)
                        .unset("leaseOwner")
                        .unset("leaseUntil"),
                PublishingPropagationHandler.COLLECTION
        );
        return result.getModifiedCount() == 1;
    }
}
