package com.storyplatform.unit.analytics.infrastructure;

import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoCollection;
import com.storyplatform.analytics.infrastructure.persistence
        .MongoTeamAnalyticsRepository;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MongoTeamAnalyticsRepositoryTest {

    @Test
    @SuppressWarnings("unchecked")
    void readsBoundedDailyProjectionAndMergesReasonMaps() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        MongoCollection<Document> collection = mock(MongoCollection.class);
        AggregateIterable<Document> aggregate =
                mock(AggregateIterable.class);
        ArgumentCaptor<List<Document>> pipeline =
                ArgumentCaptor.forClass(List.class);
        when(mongo.getCollection("reading_view_aggregates"))
                .thenReturn(collection);
        when(collection.aggregate(pipeline.capture())).thenReturn(aggregate);
        when(aggregate.allowDiskUse(true)).thenReturn(aggregate);
        doAnswer(invocation -> {
            Consumer<Document> consumer = invocation.getArgument(0);
            consumer.accept(new Document()
                    .append("_id", Date.from(Instant.EPOCH))
                    .append("rawEvents", 20)
                    .append("completedViews", 12L)
                    .append("validViews", 9L)
                    .append("invalidViews", 3L)
                    .append("reasonMaps", List.of(
                            new Document("DUPLICATE", 2),
                            new Document("DUPLICATE", 1)
                                    .append("BOT_SIGNAL", 1)
                    )));
            return null;
        }).when(aggregate).forEach(any(Consumer.class));
        var repository = new MongoTeamAnalyticsRepository(mongo);

        var rows = repository.daily(
                "team",
                Instant.EPOCH,
                Instant.EPOCH.plusSeconds(86400)
        );

        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().rawEvents()).isEqualTo(20);
        assertThat(rows.getFirst().reasonCounts())
                .containsEntry("DUPLICATE", 3L)
                .containsEntry("BOT_SIGNAL", 1L);
        assertThat(pipeline.getValue().toString()).contains(
                "period=DAY",
                "story.teamId=team",
                "$sum=$validViews",
                "$push=$reasonCounts"
        );
    }
}
