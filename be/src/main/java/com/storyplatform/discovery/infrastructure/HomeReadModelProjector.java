package com.storyplatform.discovery.infrastructure;

import com.storyplatform.discovery.application.HomeOperations;
import com.storyplatform.discovery.application.HomeService;
import com.storyplatform.shared.events.IntegrationEventHandler;
import com.storyplatform.shared.events.OutboxDelivery;

import java.util.Objects;

public final class HomeReadModelProjector
        implements IntegrationEventHandler {

    public static final String EVENT_TYPE = "catalog.story.changed";

    private final HomeOperations homes;

    public HomeReadModelProjector(HomeOperations homes) {
        this.homes = Objects.requireNonNull(homes, "homes");
    }

    @Override
    public String consumer() {
        return "home-read-model-v1";
    }

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    @Override
    public int eventVersion() {
        return 1;
    }

    @Override
    public void handle(OutboxDelivery event) {
        if (!"story".equals(event.aggregateType())) {
            throw new IllegalArgumentException(
                    "home projection requires a story aggregate"
            );
        }
        homes.rebuild(HomeService.DEFAULT_LOCALE, event.eventId());
    }
}
