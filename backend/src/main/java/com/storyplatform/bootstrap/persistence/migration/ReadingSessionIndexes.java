package com.storyplatform.bootstrap.persistence.migration;

import com.storyplatform.reading.infrastructure.persistence
        .MongoReadingSessionRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

import java.time.Duration;

public final class ReadingSessionIndexes implements MongoMigration {

    private static final String CHECKSUM =
            "b86473765705354798899bc27db00e17dded68ed65ab51d73681732bd96d2210";

    @Override
    public long version() {
        return 32;
    }

    @Override
    public String name() {
        return "create privacy bounded reading sessions";
    }

    @Override
    public String checksum() {
        return CHECKSUM;
    }

    @Override
    public void apply(MongoTemplate mongo) {
        var indexes = mongo.indexOps(
                MongoReadingSessionRepository.COLLECTION
        );
        indexes.createIndex(new Index()
                .named("reading_session_actor_started")
                .on("actorRef", Sort.Direction.ASC)
                .on("startedAt", Sort.Direction.DESC));
        indexes.createIndex(new Index()
                .named("reading_session_purge_ttl")
                .on("purgeAt", Sort.Direction.ASC)
                .expire(Duration.ZERO));
    }
}
