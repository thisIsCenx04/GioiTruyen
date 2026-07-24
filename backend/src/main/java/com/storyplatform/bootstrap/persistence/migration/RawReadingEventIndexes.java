package com.storyplatform.bootstrap.persistence.migration;

import com.storyplatform.analytics.infrastructure.persistence
        .MongoRawReadingEventRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

import java.time.Duration;

public final class RawReadingEventIndexes implements MongoMigration {

    private static final String CHECKSUM =
            "01c46b4fd737b3d8df152256151fb6035527b77dbbbf784081f2ea79661cda37";

    @Override
    public long version() {
        return 41;
    }

    @Override
    public String name() {
        return "index partitioned raw reading event buckets and retention";
    }

    @Override
    public String checksum() {
        return CHECKSUM;
    }

    @Override
    public void apply(MongoTemplate mongo) {
        var indexes = mongo.indexOps(
                MongoRawReadingEventRepository.COLLECTION
        );
        indexes.createIndex(new Index()
                .named("raw_reading_bucket_scan")
                .on("bucketStart", Sort.Direction.ASC)
                .on("_id", Sort.Direction.ASC));
        indexes.createIndex(new Index()
                .named("raw_reading_bucket_retention_ttl")
                .on("expiresAt", Sort.Direction.ASC)
                .expire(Duration.ZERO));
    }
}
