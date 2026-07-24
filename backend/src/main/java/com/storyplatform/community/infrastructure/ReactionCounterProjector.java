package com.storyplatform.community.infrastructure;

import com.storyplatform.community.application.ReactionService;
import com.storyplatform.community.domain.Reaction;
import com.storyplatform.shared.events.IntegrationEventHandler;
import com.storyplatform.shared.events.OutboxDelivery;
import tools.jackson.databind.ObjectMapper;

import java.util.Objects;

public final class ReactionCounterProjector
        implements IntegrationEventHandler {

    private final ReactionCounterStore counters;
    private final ObjectMapper mapper;

    public ReactionCounterProjector(
            ReactionCounterStore counters,
            ObjectMapper mapper
    ) {
        this.counters = Objects.requireNonNull(counters);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public String consumer() {
        return "reaction-counter";
    }

    @Override
    public String eventType() {
        return ReactionService.EVENT_TYPE;
    }

    @Override
    public int eventVersion() {
        return 1;
    }

    @Override
    public void handle(OutboxDelivery event) {
        try {
            ReactionService.ReactionChanged payload = mapper.readValue(
                    event.payload(),
                    ReactionService.ReactionChanged.class
            );
            String prefix = payload.targetType()
                    + ":" + payload.targetId() + ":";
            if (!event.aggregateId().startsWith(prefix)) {
                throw new IllegalArgumentException(
                        "reaction event identifier mismatch"
                );
            }
            counters.applyDelta(
                    Reaction.TargetType.valueOf(payload.targetType()),
                    payload.targetId(),
                    payload.delta()
            );
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "reaction event payload is invalid",
                    exception
            );
        }
    }
}
