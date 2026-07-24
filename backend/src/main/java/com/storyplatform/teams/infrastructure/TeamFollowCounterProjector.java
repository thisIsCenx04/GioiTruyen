package com.storyplatform.teams.infrastructure;

import com.storyplatform.shared.events.IntegrationEventHandler;
import com.storyplatform.shared.events.OutboxDelivery;
import com.storyplatform.teams.application.TeamFollowUseCase;
import tools.jackson.databind.ObjectMapper;

import java.util.Objects;

public final class TeamFollowCounterProjector
        implements IntegrationEventHandler {

    private final TeamFollowCounterStore counters;
    private final ObjectMapper objectMapper;

    public TeamFollowCounterProjector(
            TeamFollowCounterStore counters,
            ObjectMapper objectMapper
    ) {
        this.counters = Objects.requireNonNull(counters, "counters");
        this.objectMapper = Objects.requireNonNull(
                objectMapper,
                "objectMapper"
        );
    }

    @Override
    public String consumer() {
        return "team-follow-counter";
    }

    @Override
    public String eventType() {
        return TeamFollowUseCase.EVENT_TYPE;
    }

    @Override
    public int eventVersion() {
        return 1;
    }

    @Override
    public void handle(OutboxDelivery event) {
        try {
            TeamFollowUseCase.FollowChanged payload =
                    objectMapper.readValue(
                            event.payload(),
                            TeamFollowUseCase.FollowChanged.class
                    );
            if (!payload.teamId().equals(event.teamId())) {
                throw new IllegalArgumentException(
                        "follow event Team identifier mismatch"
                );
            }
            counters.applyDelta(payload.teamId(), payload.delta());
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "follow event payload is invalid",
                    exception
            );
        }
    }
}
