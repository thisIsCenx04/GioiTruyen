package com.storyplatform.community.infrastructure;

import com.storyplatform.community.application.StoryRelationService;
import com.storyplatform.community.domain.StoryRelation;
import com.storyplatform.shared.events.IntegrationEventHandler;
import com.storyplatform.shared.events.OutboxDelivery;
import tools.jackson.databind.ObjectMapper;

import java.util.Objects;

public final class StoryRelationCounterProjector
        implements IntegrationEventHandler {

    private final StoryRelationCounterStore counters;
    private final ObjectMapper mapper;

    public StoryRelationCounterProjector(
            StoryRelationCounterStore counters,
            ObjectMapper mapper
    ) {
        this.counters = Objects.requireNonNull(counters);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public String consumer() {
        return "story-relation-counter";
    }

    @Override
    public String eventType() {
        return StoryRelationService.EVENT_TYPE;
    }

    @Override
    public int eventVersion() {
        return 1;
    }

    @Override
    public void handle(OutboxDelivery event) {
        try {
            var payload = mapper.readValue(
                    event.payload(),
                    StoryRelationService.RelationChanged.class
            );
            if (!event.aggregateId().startsWith(
                    payload.storyId() + ":"
            )) {
                throw new IllegalArgumentException(
                        "story relation event identifier mismatch"
                );
            }
            counters.applyDelta(
                    payload.storyId(),
                    StoryRelation.Type.valueOf(payload.type()),
                    payload.delta()
            );
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "story relation event payload is invalid",
                    exception
            );
        }
    }
}
