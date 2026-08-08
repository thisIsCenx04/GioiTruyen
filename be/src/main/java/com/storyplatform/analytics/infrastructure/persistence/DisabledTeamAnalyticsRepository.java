package com.storyplatform.analytics.infrastructure.persistence;

import com.storyplatform.analytics.application.port.TeamAnalyticsRepository;

import java.time.Instant;
import java.util.List;

public class DisabledTeamAnalyticsRepository implements TeamAnalyticsRepository {

    @Override
    public List<DailyAggregate> daily(String teamId, Instant from, Instant to) {
        return List.of();
    }
}
