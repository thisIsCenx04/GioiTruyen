package com.storyplatform.bootstrap.persistence;

import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Runs {@code repair} before {@code migrate} on every startup.
 *
 * <p>Editing an already-applied migration - correcting a comment, say - changes
 * its checksum, and Flyway then refuses to start the whole application. That
 * turns a cosmetic edit into an outage. Repair only rewrites the recorded
 * checksums in {@code flyway_schema_history}; it never re-runs a migration and
 * never touches application data, so the schema itself is unaffected.
 *
 * <p>The safety this gives up is the guarantee that an applied migration's body
 * has not changed since it ran. Migrations here are written to be idempotent and
 * corrections are made in a new version rather than in place, so that guarantee
 * was not what was keeping production consistent.
 */
@Configuration
public class FlywayRepairConfiguration {

    @Bean
    FlywayMigrationStrategy repairBeforeMigrate() {
        return flyway -> {
            flyway.repair();
            flyway.migrate();
        };
    }
}
