package com.storyplatform.integration.catalog;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.storyplatform.bootstrap.persistence.migration.ChapterIndexes;
import com.storyplatform.catalog.application.PublicChapterProjection;
import com.storyplatform.catalog.application.port.ChapterRepository;
import com.storyplatform.catalog.infrastructure.persistence
        .MongoChapterDocument;
import com.storyplatform.catalog.infrastructure.persistence
        .MongoChapterRepository;
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

@Testcontainers(disabledWithoutDocker = true)
class ChapterVisibilityIntegrationTest {

    @Container
    static final MongoDBContainer MONGO =
            new MongoDBContainer("mongo:8.0.28");

    private static MongoClient client;
    private static MongoTemplate mongo;

    @BeforeAll
    static void setUp() {
        client = MongoClients.create(MONGO.getConnectionString());
        mongo = new MongoTemplate(client, "chapter_visibility");
        new ChapterIndexes().apply(mongo);
    }

    @AfterAll
    static void close() {
        if (client != null) {
            client.close();
        }
    }

    @Test
    void hiddenChaptersNeverAppearInThePublicList() {
        String storyId = "10000000-0000-4000-8000-000000000001";
        mongo.getCollection(MongoChapterDocument.COLLECTION)
                .insertMany(List.of(
                        chapter("20000000-0000-4000-8000-000000000001",
                                storyId, 1, "PUBLISHED"),
                        chapter("20000000-0000-4000-8000-000000000002",
                                storyId, 2, "HIDDEN")
                ));

        List<PublicChapterProjection> result =
                new MongoChapterRepository(mongo).findPublished(
                        new ChapterRepository.ChapterListQuery(
                                storyId, null, null, 20
                        )
                );

        assertThat(result)
                .extracting(PublicChapterProjection::number)
                .containsExactly(1);
    }

    private static Document chapter(
            String id,
            String storyId,
            int number,
            String status
    ) {
        Instant now = Instant.parse("2026-07-24T00:00:00Z");
        return new Document("_id", id)
                .append("storyId", storyId)
                .append("teamId",
                        "10000000-0000-4000-8000-000000000002")
                .append("number", number)
                .append("slug", "chapter-" + number)
                .append("title", "Chapter " + number)
                .append("workflowStatus", status)
                .append("currentRevision",
                        "30000000-0000-4000-8000-00000000000" + number)
                .append("content", "must never be projected")
                .append("publishedAt", now)
                .append("createdAt", now)
                .append("updatedAt", now)
                .append("version", 1L);
    }
}
