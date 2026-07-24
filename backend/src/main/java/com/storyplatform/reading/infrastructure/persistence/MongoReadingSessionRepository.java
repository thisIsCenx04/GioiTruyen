package com.storyplatform.reading.infrastructure.persistence;

import com.storyplatform.reading.application.port.ReadingSessionRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.util.Objects;

public final class MongoReadingSessionRepository
        implements ReadingSessionRepository {

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
                        0L
                ),
                COLLECTION
        );
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
            long lastSequence
    ) {
    }
}
