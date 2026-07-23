package com.storyplatform.integration.persistence;

import com.mongodb.ReadConcern;
import com.mongodb.WriteConcern;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

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

    private static final class RollbackProbeException
            extends RuntimeException {
    }
}
