package com.storyplatform.bootstrap.persistence.migration;

import org.bson.Document;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

public class MongoMigrationStore {

    public static final String MIGRATIONS_COLLECTION = "_schema_migrations";
    public static final String LOCKS_COLLECTION = "_migration_locks";

    private static final String SCHEMA_LOCK_ID = "schema";

    private final MongoTemplate mongoTemplate;

    public MongoMigrationStore(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public Map<Long, AppliedMigration> loadApplied() {
        Map<Long, AppliedMigration> applied = new LinkedHashMap<>();
        for (Document document : mongoTemplate.findAll(
                Document.class,
                MIGRATIONS_COLLECTION
        )) {
            Number version = document.get("_id", Number.class);
            Date appliedAt = document.getDate("appliedAt");
            AppliedMigration migration = new AppliedMigration(
                    version.longValue(),
                    document.getString("name"),
                    document.getString("checksum"),
                    appliedAt.toInstant()
            );
            applied.put(migration.version(), migration);
        }
        return Map.copyOf(applied);
    }

    public MigrationLock acquire(
            String owner,
            Instant now,
            Duration leaseDuration
    ) {
        Query availableLock = Query.query(
                Criteria.where("_id").is(SCHEMA_LOCK_ID)
        ).addCriteria(new Criteria().orOperator(
                Criteria.where("expiresAt").lte(Date.from(now)),
                Criteria.where("owner").is(owner)
        ));
        Update claim = new Update()
                .set("owner", owner)
                .set("expiresAt", Date.from(now.plus(leaseDuration)))
                .set("updatedAt", Date.from(now))
                .setOnInsert("_id", SCHEMA_LOCK_ID)
                .setOnInsert("createdAt", Date.from(now))
                .inc("fencingToken", 1);

        try {
            Document lock = mongoTemplate.findAndModify(
                    availableLock,
                    claim,
                    FindAndModifyOptions.options().upsert(true).returnNew(true),
                    Document.class,
                    LOCKS_COLLECTION
            );
            if (lock == null) {
                throw unavailableLock();
            }
            Number fencingToken = lock.get("fencingToken", Number.class);
            return new MigrationLock(owner, fencingToken.longValue());
        } catch (DuplicateKeyException exception) {
            throw unavailableLock(exception);
        }
    }

    public void renew(
            MigrationLock lock,
            Instant now,
            Duration leaseDuration
    ) {
        Query ownedLock = ownedLock(lock);
        Update renewal = new Update()
                .set("expiresAt", Date.from(now.plus(leaseDuration)))
                .set("updatedAt", Date.from(now));

        long matched = mongoTemplate.updateFirst(
                ownedLock,
                renewal,
                LOCKS_COLLECTION
        ).getMatchedCount();
        if (matched != 1) {
            throw new IllegalStateException(
                    "MongoDB migration lock lease was lost"
            );
        }
    }

    public void markApplied(
            MongoMigration migration,
            Instant appliedAt
    ) {
        mongoTemplate.insert(
                new Document("_id", migration.version())
                        .append("name", migration.name())
                        .append("checksum", migration.checksum())
                        .append("appliedAt", Date.from(appliedAt)),
                MIGRATIONS_COLLECTION
        );
    }

    public void release(MigrationLock lock) {
        mongoTemplate.remove(ownedLock(lock), LOCKS_COLLECTION);
    }

    private static Query ownedLock(MigrationLock lock) {
        return Query.query(Criteria.where("_id").is(SCHEMA_LOCK_ID)
                .and("owner").is(lock.owner())
                .and("fencingToken").is(lock.fencingToken()));
    }

    private static IllegalStateException unavailableLock() {
        return new IllegalStateException(
                "Another MongoDB migration job owns the schema lock"
        );
    }

    private static IllegalStateException unavailableLock(
            RuntimeException cause
    ) {
        return new IllegalStateException(
                "Another MongoDB migration job owns the schema lock",
                cause
        );
    }
}
