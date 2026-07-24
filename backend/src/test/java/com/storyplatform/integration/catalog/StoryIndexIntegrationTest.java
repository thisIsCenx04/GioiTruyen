package com.storyplatform.integration.catalog;

import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.storyplatform.bootstrap.persistence.migration.StoryIndexes;
import com.storyplatform.catalog.infrastructure.persistence
        .MongoStoryDocument;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class StoryIndexIntegrationTest {

    private static final String DATABASE = "story_indexes";

    @Container
    static final MongoDBContainer MONGO =
            new MongoDBContainer("mongo:8.0.28");

    private static MongoClient client;
    private static MongoTemplate mongo;

    @BeforeAll
    static void setUp() {
        client = MongoClients.create(MONGO.getConnectionString());
        mongo = new MongoTemplate(client, DATABASE);
        new StoryIndexes().apply(mongo);
    }

    @AfterAll
    static void close() {
        if (client != null) {
            client.close();
        }
    }

    @Test
    void uniqueSlugIndexRejectsASecondStory() {
        mongo.getCollection(MongoStoryDocument.COLLECTION)
                .insertOne(document("story-1", "same-slug", 1));

        assertThatThrownBy(() ->
                mongo.getCollection(MongoStoryDocument.COLLECTION)
                        .insertOne(document("story-2", "same-slug", 2)))
                .isInstanceOf(MongoWriteException.class);
    }

    @Test
    void publicLatestQueryUsesTheCompoundIndexWithoutAnInMemorySort() {
        mongo.getCollection(MongoStoryDocument.COLLECTION)
                .insertOne(document("story-3", "story-three", 3));
        Document command = new Document(
                "explain",
                new Document("find", MongoStoryDocument.COLLECTION)
                        .append(
                                "filter",
                                new Document(
                                        "workflowStatus",
                                        "PUBLISHED"
                                )
                        )
                        .append(
                                "sort",
                                new Document("publishedAt", -1)
                                        .append("_id", -1)
                        )
                        .append("hint", "story_public_latest")
        ).append("verbosity", "executionStats");

        String plan = mongo.getDb().runCommand(command).toJson();

        assertThat(plan)
                .contains("story_public_latest")
                .doesNotContain("\"stage\": \"SORT\"");
    }

    private static Document document(
            String id,
            String slug,
            int offset
    ) {
        return new Document("_id", id)
                .append("teamId", "team-1")
                .append("slug", slug)
                .append("title", "Story " + offset)
                .append("aliases", List.of())
                .append("synopsis", "Synopsis")
                .append("categoryIds", List.of("category-1"))
                .append("origin", "ORIGINAL")
                .append("language", "vi")
                .append("completionStatus", "ONGOING")
                .append("workflowStatus", "PUBLISHED")
                .append("currentRevision", "revision-" + offset)
                .append(
                        "publishedAt",
                        Instant.parse("2026-07-24T00:00:00Z")
                                .plusSeconds(offset)
                )
                .append("createdAt", Instant.EPOCH)
                .append("updatedAt", Instant.EPOCH.plusSeconds(offset))
                .append("version", 1L);
    }
}
