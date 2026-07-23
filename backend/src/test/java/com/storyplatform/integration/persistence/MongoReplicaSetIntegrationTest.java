package com.storyplatform.integration.persistence;

import com.mongodb.ReadConcern;
import com.mongodb.WriteConcern;
import com.storyplatform.bootstrap.persistence.migration.MigrationLock;
import com.storyplatform.bootstrap.persistence.migration.MigrationMetadataIndexes;
import com.storyplatform.bootstrap.persistence.migration.MongoMigration;
import com.storyplatform.bootstrap.persistence.migration.MongoMigrationRunner;
import com.storyplatform.bootstrap.persistence.migration.MongoMigrationStore;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class MongoReplicaSetIntegrationTest {

    private static final String COLLECTION = "transaction_probe";

    @Container
    static final MongoDBContainer MONGO =
            new MongoDBContainer("mongo:8.0.28").withReplicaSet();

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.mongodb.uri",
                () -> MONGO.getReplicaSetUrl("story_platform_test")
        );
        registry.add(
                "spring.mongodb.database",
                () -> "story_platform_test"
        );
    }

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void clearProbeCollection() {
        mongoTemplate.dropCollection(COLLECTION);
        mongoTemplate.dropCollection(
                MongoMigrationStore.MIGRATIONS_COLLECTION
        );
        mongoTemplate.dropCollection(MongoMigrationStore.LOCKS_COLLECTION);
    }

    @Test
    void runtimeUsesReplicaSetAndDurableClientConcerns() {
        Document hello = mongoTemplate.executeCommand("{ hello: 1 }");

        assertThat(hello.getString("setName")).isNotBlank();
        assertThat(mongoTemplate.getDb().getReadConcern())
                .isEqualTo(ReadConcern.MAJORITY);
        assertThat(mongoTemplate.getDb().getWriteConcern().getWObject())
                .isEqualTo(WriteConcern.MAJORITY.getWObject());
    }

    @Test
    void transactionRollbackDoesNotLeavePartialDocuments() {
        TransactionTemplate transaction = new TransactionTemplate(
                transactionManager
        );

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            mongoTemplate.insert(
                    new Document("_id", "first").append("value", 1),
                    COLLECTION
            );
            mongoTemplate.insert(
                    new Document("_id", "second").append("value", 2),
                    COLLECTION
            );
            throw new RollbackProbeException();
        })).isInstanceOf(RollbackProbeException.class);

        assertThat(mongoTemplate.getCollection(COLLECTION).countDocuments())
                .isZero();
    }

    @Test
    void migrationRunnerIsRepeatableAndCreatesLeaseTtlIndex() {
        MongoMigrationRunner runner = migrationRunner(
                List.of(new MigrationMetadataIndexes())
        );

        MongoMigrationRunner.MigrationRunResult first = runner.run(
                false,
                "integration-owner",
                Duration.ofMinutes(15)
        );
        MongoMigrationRunner.MigrationRunResult second = runner.run(
                false,
                "integration-owner",
                Duration.ofMinutes(15)
        );

        assertThat(first.executed()).hasSize(1);
        assertThat(second.executed()).isEmpty();
        assertThat(mongoTemplate.getCollection(
                MongoMigrationStore.MIGRATIONS_COLLECTION
        ).countDocuments()).isEqualTo(1);
        assertThat(mongoTemplate.indexOps(
                MongoMigrationStore.LOCKS_COLLECTION
        ).getIndexInfo()).anySatisfy(index -> {
            assertThat(index.getName())
                    .isEqualTo("migration_lock_expiry_ttl");
            assertThat(index.getExpireAfter()).isEqualTo(Duration.ZERO);
        });
    }

    @Test
    void interruptedIdempotentMigrationCanBeRetriedSafely() {
        AtomicBoolean firstAttempt = new AtomicBoolean(true);
        MongoMigration retryableMigration = new MongoMigration() {
            @Override
            public long version() {
                return 2;
            }

            @Override
            public String name() {
                return "retry-safe probe";
            }

            @Override
            public String checksum() {
                return "a".repeat(64);
            }

            @Override
            public void apply(MongoTemplate template) {
                template.upsert(
                        Query.query(
                                org.springframework.data.mongodb.core.query
                                        .Criteria.where("_id").is("probe")
                        ),
                        new Update().setOnInsert("value", 1),
                        COLLECTION
                );
                if (firstAttempt.getAndSet(false)) {
                    throw new IllegalStateException("simulated interruption");
                }
            }
        };
        MongoMigrationRunner runner = migrationRunner(
                List.of(retryableMigration)
        );

        assertThatThrownBy(() -> runner.run(
                false,
                "integration-owner",
                Duration.ofMinutes(15)
        )).hasMessage("simulated interruption");

        MongoMigrationRunner.MigrationRunResult retry = runner.run(
                false,
                "integration-owner",
                Duration.ofMinutes(15)
        );

        assertThat(retry.executed()).hasSize(1);
        assertThat(mongoTemplate.getCollection(COLLECTION).countDocuments())
                .isEqualTo(1);
        assertThat(mongoTemplate.getCollection(
                MongoMigrationStore.MIGRATIONS_COLLECTION
        ).countDocuments()).isEqualTo(1);
    }

    @Test
    void activeMigrationLeaseRejectsACompetingOwner() {
        MongoMigrationStore store = new MongoMigrationStore(mongoTemplate);
        MigrationLock lock = store.acquire(
                "first-owner",
                Clock.systemUTC().instant(),
                Duration.ofMinutes(15)
        );

        assertThatThrownBy(() -> store.acquire(
                "competing-owner",
                Clock.systemUTC().instant(),
                Duration.ofMinutes(15)
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "Another MongoDB migration job owns the schema lock"
                );

        store.release(lock);
    }

    private MongoMigrationRunner migrationRunner(
            List<MongoMigration> migrations
    ) {
        return new MongoMigrationRunner(
                migrations,
                new MongoMigrationStore(mongoTemplate),
                mongoTemplate,
                Clock.systemUTC()
        );
    }

    private static final class RollbackProbeException
            extends RuntimeException {
    }
}
