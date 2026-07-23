package com.storyplatform.unit.bootstrap.persistence.migration;

import com.storyplatform.bootstrap.persistence.migration.AppliedMigration;
import com.storyplatform.bootstrap.persistence.migration.MigrationLock;
import com.storyplatform.bootstrap.persistence.migration.MongoMigration;
import com.storyplatform.bootstrap.persistence.migration.MongoMigrationRunner;
import com.storyplatform.bootstrap.persistence.migration.MongoMigrationStore;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MongoMigrationRunnerTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Duration LEASE = Duration.ofMinutes(15);
    private static final String CHECKSUM_ONE = "1".repeat(64);
    private static final String CHECKSUM_TWO = "2".repeat(64);

    private final MongoMigrationStore store = mock(MongoMigrationStore.class);
    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void dryRunReportsPendingVersionsInOrderWithoutWriting() {
        MongoMigration second = migration(2, "second", CHECKSUM_TWO);
        MongoMigration first = migration(1, "first", CHECKSUM_ONE);
        when(store.loadApplied()).thenReturn(Map.of());
        MongoMigrationRunner runner = runner(second, first);

        MongoMigrationRunner.MigrationRunResult result = runner.run(
                true,
                "dry-run-owner",
                LEASE
        );

        assertThat(result.dryRun()).isTrue();
        assertThat(result.pending())
                .extracting(MongoMigrationRunner.MigrationDescriptor::version)
                .containsExactly(1L, 2L);
        assertThat(result.executed()).isEmpty();
        verify(store, never()).acquire(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
        verify(first, never()).apply(mongoTemplate);
        verify(second, never()).apply(mongoTemplate);
    }

    @Test
    void executionSkipsAppliedVersionAndRecordsPendingVersion() {
        MongoMigration first = migration(1, "first", CHECKSUM_ONE);
        MongoMigration second = migration(2, "second", CHECKSUM_TWO);
        AppliedMigration applied = new AppliedMigration(
                1,
                "first",
                CHECKSUM_ONE,
                NOW.minusSeconds(60)
        );
        MigrationLock lock = new MigrationLock("job-owner", 7);
        when(store.loadApplied()).thenReturn(Map.of(1L, applied));
        when(store.acquire("job-owner", NOW, LEASE)).thenReturn(lock);
        MongoMigrationRunner runner = runner(first, second);

        MongoMigrationRunner.MigrationRunResult result = runner.run(
                false,
                "job-owner",
                LEASE
        );

        assertThat(result.executed())
                .extracting(MongoMigrationRunner.MigrationDescriptor::version)
                .containsExactly(2L);
        verify(first, never()).apply(mongoTemplate);
        var ordered = inOrder(store, second, store);
        ordered.verify(store).renew(lock, NOW, LEASE);
        ordered.verify(second).apply(mongoTemplate);
        ordered.verify(store).renew(lock, NOW, LEASE);
        ordered.verify(store).markApplied(second, NOW);
        verify(store).release(lock);
    }

    @Test
    void changedChecksumIsRejectedBeforeLockAcquisition() {
        MongoMigration migration = migration(1, "first", CHECKSUM_TWO);
        when(store.loadApplied()).thenReturn(Map.of(
                1L,
                new AppliedMigration(1, "first", CHECKSUM_ONE, NOW)
        ));
        MongoMigrationRunner runner = runner(migration);

        assertThatIllegalStateException().isThrownBy(() -> runner.run(
                false,
                "job-owner",
                LEASE
        )).withMessage("Checksum mismatch for applied MongoDB migration 1");

        verify(store, never()).acquire(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void failedMigrationIsNotRecordedAndLeaseIsReleased() {
        MongoMigration migration = migration(1, "first", CHECKSUM_ONE);
        MigrationLock lock = new MigrationLock("job-owner", 11);
        when(store.loadApplied()).thenReturn(Map.of());
        when(store.acquire("job-owner", NOW, LEASE)).thenReturn(lock);
        doThrow(new IllegalStateException("migration failed"))
                .when(migration)
                .apply(mongoTemplate);
        MongoMigrationRunner runner = runner(migration);

        assertThatIllegalStateException().isThrownBy(() -> runner.run(
                false,
                "job-owner",
                LEASE
        )).withMessage("migration failed");

        verify(store, never()).markApplied(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
        verify(store).release(lock);
    }

    @Test
    void duplicateVersionsAreRejectedAtConstruction() {
        MongoMigration first = migration(1, "first", CHECKSUM_ONE);
        MongoMigration duplicate = migration(1, "duplicate", CHECKSUM_TWO);

        assertThatIllegalArgumentException().isThrownBy(() ->
                runner(first, duplicate)
        ).withMessage("Duplicate MongoDB migration version 1");
    }

    private MongoMigrationRunner runner(MongoMigration... migrations) {
        return new MongoMigrationRunner(
                List.of(migrations),
                store,
                mongoTemplate,
                clock
        );
    }

    private static MongoMigration migration(
            long version,
            String name,
            String checksum
    ) {
        MongoMigration migration = mock(MongoMigration.class);
        when(migration.version()).thenReturn(version);
        when(migration.name()).thenReturn(name);
        when(migration.checksum()).thenReturn(checksum);
        return migration;
    }
}
