package com.storyplatform.unit.analytics.infrastructure;

import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoCollection;
import com.storyplatform.analytics.infrastructure.persistence
        .MongoRewardViewAggregateDirectory;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MongoRewardViewAggregateDirectoryTest {

    @Test
    void readsOnlyVersionedDailyValidViewsGroupedByTeam() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        MongoCollection<Document> collection = mock(MongoCollection.class);
        AggregateIterable<Document> aggregate =
                mock(AggregateIterable.class);
        ArgumentCaptor<List<Document>> pipeline =
                ArgumentCaptor.forClass(List.class);
        when(mongo.getCollection("reading_view_aggregates"))
                .thenReturn(collection);
        when(collection.aggregate(pipeline.capture()))
                .thenReturn(aggregate);
        when(aggregate.allowDiskUse(true)).thenReturn(aggregate);
        doAnswer(invocation -> {
            Consumer<Document> consumer = invocation.getArgument(0);
            consumer.accept(new Document("_id", "team")
                    .append("validViews", 42L));
            return null;
        }).when(aggregate).forEach(any(Consumer.class));
        var directory = new MongoRewardViewAggregateDirectory(mongo);

        var rows = directory.validViewsByTeam(
                Instant.parse("2026-07-24T00:00:00Z"),
                Instant.parse("2026-07-25T00:00:00Z")
        );

        assertThat(rows).containsExactly(
                new com.storyplatform.analytics.application.contract
                        .RewardViewAggregateDirectory.TeamValidViews(
                        "team",
                        42
                )
        );
        assertThat(directory.aggregateVersion())
                .isEqualTo("view-aggregate-2026.1");
        assertThat(pipeline.getValue().toString())
                .contains(
                        "period=DAY",
                        "aggregateVersion=view-aggregate-2026.1",
                        "$sum=$validViews"
                );
    }

    @Test
    void failsClosedWhenAggregateAmountIsMissing() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        MongoCollection<Document> collection = mock(MongoCollection.class);
        AggregateIterable<Document> aggregate =
                mock(AggregateIterable.class);
        when(mongo.getCollection(any())).thenReturn(collection);
        when(collection.aggregate(any())).thenReturn(aggregate);
        when(aggregate.allowDiskUse(true)).thenReturn(aggregate);
        doAnswer(invocation -> {
            Consumer<Document> consumer = invocation.getArgument(0);
            consumer.accept(new Document("_id", "team"));
            return null;
        }).when(aggregate).forEach(any(Consumer.class));

        assertThatThrownBy(() ->
                new MongoRewardViewAggregateDirectory(mongo)
                        .validViewsByTeam(
                                Instant.EPOCH,
                                Instant.EPOCH.plusSeconds(86_400)
                        )
        ).isInstanceOf(IllegalStateException.class);
    }
}
