package com.storyplatform.bootstrap.persistence.migration;

import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class MongoMigrationRunner {

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    private final List<MongoMigration> migrations;
    private final MongoMigrationStore store;
    private final MongoTemplate mongoTemplate;
    private final Clock clock;

    public MongoMigrationRunner(
            List<MongoMigration> migrations,
            MongoMigrationStore store,
            MongoTemplate mongoTemplate,
            Clock clock
    ) {
        this.migrations = validateAndSort(migrations);
        this.store = store;
        this.mongoTemplate = mongoTemplate;
        this.clock = clock;
    }

    public MigrationRunResult run(
            boolean dryRun,
            String owner,
            Duration leaseDuration
    ) {
        validateRuntimeSettings(owner, leaseDuration);
        Map<Long, AppliedMigration> applied = store.loadApplied();
        validateChecksums(applied);
        List<MigrationDescriptor> pending = pendingMigrations(applied);

        if (dryRun || pending.isEmpty()) {
            return new MigrationRunResult(dryRun, pending, List.of());
        }

        MigrationLock lock = store.acquire(
                owner,
                clock.instant(),
                leaseDuration
        );
        List<MigrationDescriptor> executed = new ArrayList<>();
        try {
            applied = store.loadApplied();
            validateChecksums(applied);
            pending = pendingMigrations(applied);

            for (MigrationDescriptor descriptor : pending) {
                MongoMigration migration = migration(descriptor.version());
                store.renew(lock, clock.instant(), leaseDuration);
                migration.apply(mongoTemplate);
                store.renew(lock, clock.instant(), leaseDuration);
                store.markApplied(migration, clock.instant());
                executed.add(descriptor);
            }
            return new MigrationRunResult(false, pending, List.copyOf(executed));
        } finally {
            store.release(lock);
        }
    }

    private List<MigrationDescriptor> pendingMigrations(
            Map<Long, AppliedMigration> applied
    ) {
        return migrations.stream()
                .filter(migration -> !applied.containsKey(migration.version()))
                .map(MigrationDescriptor::from)
                .toList();
    }

    private MongoMigration migration(long version) {
        return migrations.stream()
                .filter(candidate -> candidate.version() == version)
                .findFirst()
                .orElseThrow();
    }

    private void validateChecksums(Map<Long, AppliedMigration> applied) {
        for (MongoMigration migration : migrations) {
            AppliedMigration previous = applied.get(migration.version());
            if (previous != null
                    && !previous.checksum().equals(migration.checksum())) {
                throw new IllegalStateException(
                        "Checksum mismatch for applied MongoDB migration "
                                + migration.version()
                );
            }
        }
    }

    private static List<MongoMigration> validateAndSort(
            List<MongoMigration> migrations
    ) {
        Set<Long> versions = new HashSet<>();
        for (MongoMigration migration : migrations) {
            if (migration.version() <= 0) {
                throw new IllegalArgumentException(
                        "MongoDB migration version must be positive"
                );
            }
            if (!versions.add(migration.version())) {
                throw new IllegalArgumentException(
                        "Duplicate MongoDB migration version "
                                + migration.version()
                );
            }
            if (migration.name() == null || migration.name().isBlank()) {
                throw new IllegalArgumentException(
                        "MongoDB migration name must not be blank"
                );
            }
            if (migration.checksum() == null
                    || !SHA_256.matcher(migration.checksum()).matches()) {
                throw new IllegalArgumentException(
                        "MongoDB migration checksum must be lowercase SHA-256"
                );
            }
        }
        return migrations.stream()
                .sorted(Comparator.comparingLong(MongoMigration::version))
                .toList();
    }

    private static void validateRuntimeSettings(
            String owner,
            Duration leaseDuration
    ) {
        if (owner == null || owner.isBlank()) {
            throw new IllegalArgumentException(
                    "MongoDB migration lock owner must not be blank"
            );
        }
        if (leaseDuration == null
                || leaseDuration.isZero()
                || leaseDuration.isNegative()) {
            throw new IllegalArgumentException(
                    "MongoDB migration lock duration must be positive"
            );
        }
    }

    public record MigrationDescriptor(
            long version,
            String name,
            String checksum
    ) {
        private static MigrationDescriptor from(MongoMigration migration) {
            return new MigrationDescriptor(
                    migration.version(),
                    migration.name(),
                    migration.checksum()
            );
        }
    }

    public record MigrationRunResult(
            boolean dryRun,
            List<MigrationDescriptor> pending,
            List<MigrationDescriptor> executed
    ) {
    }
}
