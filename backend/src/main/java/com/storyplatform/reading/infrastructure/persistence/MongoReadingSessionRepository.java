package com.storyplatform.reading.infrastructure.persistence;

import com.storyplatform.reading.application.port.ReadingHeartbeatRepository;
import com.storyplatform.reading.application.port.ReadingCompletionRepository;
import com.storyplatform.reading.application.port.ReadingSessionRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class MongoReadingSessionRepository
        implements ReadingSessionRepository,
        ReadingHeartbeatRepository,
        ReadingCompletionRepository {

    public static final String COLLECTION = "reading_sessions";
    private final MongoTemplate mongo;

    public MongoReadingSessionRepository(MongoTemplate mongo) {
        this.mongo = Objects.requireNonNull(mongo);
    }

    @Override
    public boolean chapterIsPublished(
            String storyId,
            String chapterId
    ) {
        boolean chapter = mongo.exists(
                Query.query(new Criteria().andOperator(
                        Criteria.where("_id").is(chapterId),
                        Criteria.where("storyId").is(storyId),
                        Criteria.where("status").is("PUBLISHED"),
                        Criteria.where("publishedRevisionId").ne(null)
                )),
                "chapters"
        );
        return chapter && mongo.exists(
                Query.query(new Criteria().andOperator(
                        Criteria.where("_id").is(storyId),
                        Criteria.where("state").is("PUBLISHED")
                )),
                "stories"
        );
    }

    @Override
    public void create(SessionRecord session) {
        mongo.insert(
                new SessionDocument(
                        session.id(),
                        session.storyId(),
                        session.chapterId(),
                        session.actorType(),
                        session.actorRef(),
                        session.startedAt(),
                        session.expiresAt(),
                        session.purgeAt(),
                        "ACTIVE",
                        0L,
                        List.of(),
                        session.startedAt()
                ),
                COLLECTION
        );
    }

    @Override
    public ApplyResult apply(
            String sessionId,
            String actorRef,
            String batchId,
            long expectedPreviousSequence,
            long lastSequence,
            Instant now
    ) {
        Query query = Query.query(new Criteria().andOperator(
                Criteria.where("_id").is(sessionId),
                Criteria.where("actorRef").is(actorRef),
                Criteria.where("status").is("ACTIVE"),
                Criteria.where("expiresAt").gt(now),
                Criteria.where("lastSequence").is(
                        expectedPreviousSequence
                ),
                Criteria.where("processedBatchIds").ne(batchId)
        ));
        Update update = new Update()
                .set("lastSequence", lastSequence)
                .set("updatedAt", now)
                .addToSet("processedBatchIds", batchId);
        SessionDocument applied = mongo.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                SessionDocument.class,
                COLLECTION
        );
        if (applied != null) {
            return ApplyResult.APPLIED;
        }
        SessionDocument current = mongo.findById(
                sessionId,
                SessionDocument.class,
                COLLECTION
        );
        if (current == null
                || !actorRef.equals(current.actorRef())
                || !"ACTIVE".equals(current.status())
                || !now.isBefore(current.expiresAt())) {
            return ApplyResult.NOT_ACTIVE;
        }
        if (current.processedBatchIds() != null
                && current.processedBatchIds().contains(batchId)) {
            return ApplyResult.DUPLICATE;
        }
        return ApplyResult.SEQUENCE_CONFLICT;
    }

    @Override
    public CompleteResult complete(
            String sessionId,
            String actorRef,
            String completionId,
            long finalSequence,
            Instant completedAt,
            Instant now
    ) {
        Query apply = Query.query(new Criteria().andOperator(
                Criteria.where("_id").is(sessionId),
                Criteria.where("actorRef").is(actorRef),
                Criteria.where("status").is("ACTIVE"),
                Criteria.where("expiresAt").gt(now),
                Criteria.where("lastSequence").is(finalSequence),
                Criteria.where("completionId").ne(completionId)
        ));
        SessionDocument completed = mongo.findAndModify(
                apply,
                new Update()
                        .set("status", "COMPLETION_PENDING")
                        .set("completionId", completionId)
                        .set("completedAt", completedAt)
                        .set("updatedAt", now),
                FindAndModifyOptions.options().returnNew(true),
                SessionDocument.class,
                COLLECTION
        );
        if (completed != null) {
            return CompleteResult.APPLIED;
        }
        if (mongo.exists(
                Query.query(new Criteria().andOperator(
                        Criteria.where("_id").is(sessionId),
                        Criteria.where("actorRef").is(actorRef),
                        Criteria.where("completionId").is(completionId),
                        Criteria.where("status").is("COMPLETION_PENDING")
                )),
                COLLECTION
        )) {
            return CompleteResult.DUPLICATE;
        }
        if (mongo.exists(
                Query.query(new Criteria().andOperator(
                        Criteria.where("_id").is(sessionId),
                        Criteria.where("actorRef").is(actorRef),
                        Criteria.where("status").is("ACTIVE"),
                        Criteria.where("expiresAt").gt(now)
                )),
                COLLECTION
        )) {
            return CompleteResult.SEQUENCE_CONFLICT;
        }
        return CompleteResult.NOT_ACTIVE;
    }

    public record SessionDocument(
            String id,
            String storyId,
            String chapterId,
            String actorType,
            String actorRef,
            Instant startedAt,
            Instant expiresAt,
            Instant purgeAt,
            String status,
            long lastSequence,
            List<String> processedBatchIds,
            Instant updatedAt
    ) {
        public SessionDocument {
            processedBatchIds = processedBatchIds == null
                    ? List.of()
                    : List.copyOf(processedBatchIds);
        }
    }
}
