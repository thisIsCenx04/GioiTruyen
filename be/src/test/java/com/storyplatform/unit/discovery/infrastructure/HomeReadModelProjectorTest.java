package com.storyplatform.unit.discovery.infrastructure;

import com.storyplatform.discovery.application.HomeOperations;
import com.storyplatform.discovery.infrastructure.HomeReadModelProjector;
import com.storyplatform.shared.events.OutboxDelivery;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class HomeReadModelProjectorTest {

    @Test
    void rebuildsFromAStoryEventUsingTheEventIdAsVersion() {
        HomeOperations homes = mock(HomeOperations.class);
        new HomeReadModelProjector(homes).handle(event("story"));

        verify(homes).rebuild("vi-VN", "event-1");
    }

    @Test
    void rejectsEventsForAnotherAggregate() {
        HomeOperations homes = mock(HomeOperations.class);
        assertThatThrownBy(() ->
                new HomeReadModelProjector(homes).handle(event("team")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static OutboxDelivery event(String aggregateType) {
        return new OutboxDelivery(
                "event-1",
                HomeReadModelProjector.EVENT_TYPE,
                1,
                Instant.EPOCH,
                "correlation-1",
                aggregateType,
                "story-1",
                null,
                null,
                "application/json",
                "{}"
        );
    }
}
