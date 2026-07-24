package com.storyplatform.unit.analytics.infrastructure;

import com.storyplatform.analytics.application.RawReadingEvent;
import com.storyplatform.analytics.infrastructure.persistence
        .MongoRawReadingEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class MongoRawReadingEventRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-07-24T00:00:30Z");

    @Test
    void appendsMaximumBatchWithOneAtomicWriteForOnePartition() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        var repository = new MongoRawReadingEventRepository(
                mongo,
                Duration.ofDays(90)
        );
        List<RawReadingEvent> events = new ArrayList<>();
        for (int index = 1; index <= 20; index++) {
            events.add(event(
                    "aa" + "0".repeat(62),
                    NOW.plusSeconds(index),
                    index
            ));
        }

        repository.append(events);

        verify(mongo).upsert(
                any(Query.class),
                any(UpdateDefinition.class),
                eq(MongoRawReadingEventRepository.COLLECTION)
        );
    }

    @Test
    void partitionsByMinuteAndPseudonymPrefixWithoutReadBeforeWrite() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        var repository = new MongoRawReadingEventRepository(
                mongo,
                Duration.ofDays(90)
        );

        repository.append(List.of(
                event("aa" + "0".repeat(62), NOW, 1),
                event("bb" + "0".repeat(62), NOW, 2),
                event(
                        "aa" + "0".repeat(62),
                        NOW.plusSeconds(61),
                        3
                )
        ));

        verify(mongo, times(3)).upsert(
                any(Query.class),
                any(UpdateDefinition.class),
                eq(MongoRawReadingEventRepository.COLLECTION)
        );
        verifyNoMoreInteractions(mongo);
    }

    @Test
    void boundsRetentionAndAppendSize() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        for (Duration retention : new Duration[]{
                null,
                Duration.ofDays(6),
                Duration.ofDays(366)
        }) {
            assertThatThrownBy(() -> new MongoRawReadingEventRepository(
                    mongo,
                    retention
            )).isInstanceOf(IllegalArgumentException.class);
        }
        var repository = new MongoRawReadingEventRepository(
                mongo,
                Duration.ofDays(7)
        );
        assertThatThrownBy(() -> repository.append(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repository.append(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        List<RawReadingEvent> oversized = new ArrayList<>();
        for (int index = 0; index < 21; index++) {
            oversized.add(event("aa" + "0".repeat(62), NOW, index));
        }
        assertThatThrownBy(() -> repository.append(oversized))
                .isInstanceOf(IllegalArgumentException.class);
        List<RawReadingEvent> containingNull = new ArrayList<>();
        containingNull.add(null);
        assertThatThrownBy(() -> repository.append(containingNull))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new MongoRawReadingEventRepository(
                mongo,
                Duration.ofDays(365)
        )).isNotNull();
    }

    private static RawReadingEvent event(
            String sessionRef,
            Instant occurredAt,
            long sequence
    ) {
        return new RawReadingEvent(
                "event:" + sequence,
                RawReadingEvent.Kind.HEARTBEAT,
                sessionRef,
                "20000000-0000-4000-8000-000000000001",
                "30000000-0000-4000-8000-000000000001",
                sequence,
                occurredAt,
                50,
                10,
                NOW
        );
    }
}
