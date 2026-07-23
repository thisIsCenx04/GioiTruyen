package com.storyplatform.integration.persistence;

import com.mongodb.ReadConcern;
import com.mongodb.WriteConcern;
import com.storyplatform.bootstrap.persistence.migration.MigrationLock;
import com.storyplatform.bootstrap.persistence.migration.MigrationMetadataIndexes;
import com.storyplatform.bootstrap.persistence.migration.MongoMigration;
import com.storyplatform.bootstrap.persistence.migration.MongoMigrationRunner;
import com.storyplatform.bootstrap.persistence.migration.MongoMigrationStore;
import com.storyplatform.bootstrap.persistence.migration.OutboxInboxIndexes;
import com.storyplatform.shared.events.IntegrationEvent;
import com.storyplatform.shared.events.IntegrationEventHandler;
import com.storyplatform.shared.events.OutboxDelivery;
import com.storyplatform.shared.events.persistence.InboxDispatcher;
import com.storyplatform.shared.events.persistence.InboxReceipt;
import com.storyplatform.shared.events.persistence.OutboxAppender;
import com.storyplatform.shared.events.persistence.OutboxMessage;
import com.storyplatform.shared.events.persistence.OutboxMessageStore;
import com.storyplatform.shared.events.persistence.OutboxStatus;
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
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class MongoReplicaSetIntegrationTest {

    private static final String COLLECTION = "transaction_probe";
    private static final Instant FIXED_INSTANT = Instant.parse(
            "2026-01-01T00:00:00Z"
    );
    private static final Clock FIXED_CLOCK = Clock.fixed(
            FIXED_INSTANT,
            ZoneOffset.UTC
    );

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

    @Autowired
    private OutboxAppender outboxAppender;

    @BeforeEach
    void clearProbeCollection() {
        mongoTemplate.dropCollection(COLLECTION);
        mongoTemplate.dropCollection(
                MongoMigrationStore.MIGRATIONS_COLLECTION
        );
        mongoTemplate.dropCollection(MongoMigrationStore.LOCKS_COLLECTION);
        mongoTemplate.dropCollection(OutboxMessage.COLLECTION);
        mongoTemplate.dropCollection(InboxReceipt.COLLECTION);
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
                FIXED_INSTANT,
                Duration.ofMinutes(15)
        );

        assertThatThrownBy(() -> store.acquire(
                "competing-owner",
                FIXED_INSTANT,
                Duration.ofMinutes(15)
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "Another MongoDB migration job owns the schema lock"
                );

        store.release(lock);
    }

    @Test
    void aggregateAndOutboxMessageCommitOrRollbackTogether() {
        TransactionTemplate transaction = new TransactionTemplate(
                transactionManager
        );
        IntegrationEvent event = integrationEvent();

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            mongoTemplate.insert(
                    new Document("_id", "aggregate-rollback"),
                    COLLECTION
            );
            outboxAppender.append(event);
            throw new RollbackProbeException();
        })).isInstanceOf(RollbackProbeException.class);

        assertThat(mongoTemplate.getCollection(COLLECTION).countDocuments())
                .isZero();
        assertThat(mongoTemplate.getCollection(
                OutboxMessage.COLLECTION
        ).countDocuments()).isZero();

        transaction.executeWithoutResult(status -> {
            mongoTemplate.insert(
                    new Document("_id", "aggregate-commit"),
                    COLLECTION
            );
            outboxAppender.append(event);
        });

        assertThat(mongoTemplate.getCollection(COLLECTION).countDocuments())
                .isEqualTo(1);
        OutboxMessage stored = mongoTemplate.findById(
                event.eventId().toString(),
                OutboxMessage.class
        );
        assertThat(stored).isNotNull();
        assertThat(stored.status()).isEqualTo(OutboxStatus.PENDING);
    }

    @Test
    void outboxAndInboxIndexesSupportClaimAndConsumerDedupe() {
        migrationRunner(List.of(
                new MigrationMetadataIndexes(),
                new OutboxInboxIndexes()
        )).run(false, "integration-owner", Duration.ofMinutes(15));

        assertThat(mongoTemplate.indexOps(
                OutboxMessage.COLLECTION
        ).getIndexInfo()).anySatisfy(index ->
                assertThat(index.getName()).isEqualTo("outbox_claim_v1")
        );
        assertThat(mongoTemplate.indexOps(
                InboxReceipt.COLLECTION
        ).getIndexInfo()).anySatisfy(index -> {
            assertThat(index.getName())
                    .isEqualTo("inbox_consumer_event_unique");
            assertThat(index.isUnique()).isTrue();
        });
    }

    @Test
    void concurrentWorkersCannotClaimTheSameOutboxMessage() {
        TransactionTemplate transaction = new TransactionTemplate(
                transactionManager
        );
        transaction.executeWithoutResult(status ->
                outboxAppender.append(integrationEvent())
        );
        OutboxMessageStore store = new OutboxMessageStore(mongoTemplate);
        Instant claimTime = FIXED_INSTANT.plusSeconds(1);

        assertThat(store.claim(
                "worker-1",
                claimTime,
                Duration.ofSeconds(30)
        )).isPresent();
        assertThat(store.claim(
                "worker-2",
                claimTime,
                Duration.ofSeconds(30)
        )).isEmpty();
    }

    @Test
    void inboxReplayDoesNotRepeatTransactionalHandlerSideEffect() {
        IntegrationEventHandler handler = new IntegrationEventHandler() {
            @Override
            public String consumer() {
                return "integration-probe";
            }

            @Override
            public String eventType() {
                return "publishing.story.published";
            }

            @Override
            public int eventVersion() {
                return 1;
            }

            @Override
            public void handle(OutboxDelivery event) {
                mongoTemplate.upsert(
                        Query.query(
                                org.springframework.data.mongodb.core.query
                                        .Criteria.where("_id")
                                        .is("handler-count")
                        ),
                        new Update().inc("count", 1),
                        COLLECTION
                );
            }
        };
        InboxDispatcher dispatcher = new InboxDispatcher(
                mongoTemplate,
                List.of(handler),
                FIXED_CLOCK
        );
        OutboxMessage message = outboxMessage();
        TransactionTemplate transaction = new TransactionTemplate(
                transactionManager
        );

        transaction.execute(status -> dispatcher.dispatch(message));
        transaction.execute(status -> dispatcher.dispatch(message));

        Document result = mongoTemplate.findById(
                "handler-count",
                Document.class,
                COLLECTION
        );
        assertThat(result).isNotNull();
        assertThat(result.getInteger("count")).isEqualTo(1);
        assertThat(mongoTemplate.getCollection(
                InboxReceipt.COLLECTION
        ).countDocuments()).isEqualTo(1);
    }

    private MongoMigrationRunner migrationRunner(
            List<MongoMigration> migrations
    ) {
        return new MongoMigrationRunner(
                migrations,
                new MongoMigrationStore(mongoTemplate),
                mongoTemplate,
                FIXED_CLOCK
        );
    }

    private static IntegrationEvent integrationEvent() {
        return new IntegrationEvent(
                UUID.fromString("581c36b2-52a0-4a18-8035-5478ec1c3270"),
                "publishing.story.published",
                1,
                Instant.parse("2026-01-01T00:00:00Z"),
                "request-1",
                "story",
                "story-1",
                "user-1",
                "team-1",
                Map.of("revision", 3)
        );
    }

    private static OutboxMessage outboxMessage() {
        return new OutboxMessage(
                "event-1",
                "publishing.story.published",
                1,
                Instant.parse("2026-01-01T00:00:00Z"),
                "request-1",
                Map.of(),
                "story",
                "story-1",
                "user-1",
                "team-1",
                "application/json",
                "{\"revision\":3}",
                OutboxStatus.PROCESSING,
                1,
                Instant.parse("2026-01-01T00:00:00Z"),
                "worker-1",
                Instant.parse("2026-01-01T00:00:30Z"),
                null,
                Instant.parse("2026-01-01T00:00:00Z"),
                null
        );
    }

    private static final class RollbackProbeException
            extends RuntimeException {
    }
}
