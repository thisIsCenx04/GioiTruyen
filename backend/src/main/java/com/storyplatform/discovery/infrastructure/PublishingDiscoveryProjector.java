package com.storyplatform.discovery.infrastructure;

import com.storyplatform.discovery.application.HomeOperations;
import com.storyplatform.discovery.application.HomeService;
import com.storyplatform.shared.events.IntegrationEventHandler;
import com.storyplatform.shared.events.OutboxDelivery;

import java.util.Objects;

public final class PublishingDiscoveryProjector
        implements IntegrationEventHandler {

    private final HomeOperations homes;
    private final String eventType;

    public PublishingDiscoveryProjector(
            HomeOperations homes,
            String eventType
    ) {
        this.homes = Objects.requireNonNull(homes, "homes");
        this.eventType = Objects.requireNonNull(eventType, "eventType");
    }

    @Override
    public String consumer() {
        return "publishing-discovery-v1";
    }

    @Override
    public String eventType() {
        return eventType;
    }

    @Override
    public int eventVersion() {
        return 1;
    }

    @Override
    public void handle(OutboxDelivery event) {
        if (!"chapter".equals(event.aggregateType())
                && !"story".equals(event.aggregateType())) {
            throw new IllegalArgumentException(
                    "publishing discovery requires story or chapter"
            );
        }
        homes.rebuild(HomeService.DEFAULT_LOCALE, event.eventId());
    }
}
