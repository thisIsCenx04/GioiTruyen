package com.storyplatform.unit.discovery.infrastructure;

import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoCollection;
import com.storyplatform.discovery.application.port.RankingRepository;
import com.storyplatform.discovery.infrastructure.persistence
        .MongoRankingRepository;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MongoRankingRepositoryTest {

    private static final Instant FROM = Instant.parse("2026-07-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-08-01T00:00:00Z");

    @Test
    void storyPipelineRanksOnlyPrecomputedValidViewCounters() {
        Fixture fixture = fixture(new Document()
                .append("_id", "story")
                .append("name", "Story")
                .append("teamId", "team")
                .append("validViews", 10L)
                .append("completedViews", 12L)
                .append("invalidViews", 2L)
                .append("score", 10L));

        var result = fixture.repository.stories(
                FROM, TO, RankingRepository.Metric.VALID_VIEWS, 20
        );

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().validViews()).isEqualTo(10);
        String pipeline = fixture.pipeline().getValue().toString();
        assertThat(pipeline).contains(
                "period=DAY",
                "$sum=$validViews",
                "$limit=20"
        );
        assertThat(pipeline).doesNotContain("$sum=$rawEvents");
    }

    @Test
    void teamPipelineComputesQualityRateAndSafelyDefaultsNumbers() {
        Fixture fixture = fixture(new Document()
                .append("_id", "team")
                .append("name", "Team")
                .append("teamId", "team")
                .append("validViews", 8L)
                .append("completedViews", 10L)
                .append("invalidViews", 2L));

        var result = fixture.repository.teams(
                FROM, TO, RankingRepository.Metric.QUALITY_RATE, 10
        );

        assertThat(result.getFirst().score()).isZero();
        assertThat(fixture.pipeline().getValue().toString())
                .contains("$divide", "$invalidViews", "from=teams");
    }

    @SuppressWarnings("unchecked")
    private static Fixture fixture(Document result) {
        MongoTemplate mongo = mock(MongoTemplate.class);
        MongoCollection<Document> collection = mock(MongoCollection.class);
        AggregateIterable<Document> aggregate =
                mock(AggregateIterable.class);
        when(mongo.getCollection("reading_view_aggregates"))
                .thenReturn(collection);
        when(collection.aggregate(anyList())).thenReturn(aggregate);
        when(aggregate.allowDiskUse(true)).thenReturn(aggregate);
        doAnswer(invocation -> {
            Consumer<Document> consumer = invocation.getArgument(0);
            consumer.accept(result);
            return null;
        }).when(aggregate).forEach(any(Consumer.class));
        ArgumentCaptor<List<Document>> pipeline =
                ArgumentCaptor.forClass(List.class);
        when(collection.aggregate(pipeline.capture())).thenReturn(aggregate);
        return new Fixture(
                new MongoRankingRepository(mongo),
                pipeline
        );
    }

    private record Fixture(
            MongoRankingRepository repository,
            ArgumentCaptor<List<Document>> pipeline
    ) {
    }
}
