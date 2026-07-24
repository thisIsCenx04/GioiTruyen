package com.storyplatform.unit.discovery.infrastructure;

import com.storyplatform.discovery.application.HomeOperations;
import com.storyplatform.discovery.infrastructure
        .PublishingDiscoveryProjector;
import com.storyplatform.shared.events.OutboxDelivery;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PublishingDiscoveryProjectorTest {

    @Test
    void rebuildsPublishedDiscoveryFromSourceOfTruth() {
        HomeOperations homes = mock(HomeOperations.class);
        var projector = new PublishingDiscoveryProjector(
                homes,
                "publishing.chapter.published"
        );

        projector.handle(event("chapter"));

        assertThat(projector.consumer())
                .isEqualTo("publishing-discovery-v1");
        assertThat(projector.eventVersion()).isOne();
        verify(homes).rebuild("vi-VN", "event-1");
    }

    @Test
    void rejectsUnrelatedAggregate() {
        var projector = new PublishingDiscoveryProjector(
                mock(HomeOperations.class),
                "publishing.visibility.changed"
        );

        assertThatThrownBy(() -> projector.handle(event("user")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static OutboxDelivery event(String aggregateType) {
        return new OutboxDelivery(
                "event-1",
                "publishing.chapter.published",
                1,
                Instant.parse("2026-07-24T00:00:00Z"),
                "correlation",
                aggregateType,
                "aggregate",
                null,
                null,
                "application/json",
                "{}"
        );
    }
}
