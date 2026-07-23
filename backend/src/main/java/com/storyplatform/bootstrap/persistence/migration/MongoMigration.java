package com.storyplatform.bootstrap.persistence.migration;

import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * A forward-only, backward-compatible MongoDB schema migration.
 *
 * <p>Implementations must be idempotent because a process can stop after the
 * database change succeeds but before its completion marker is persisted.</p>
 */
public interface MongoMigration {

    long version();

    String name();

    String checksum();

    void apply(MongoTemplate mongoTemplate);
}
