package com.storyplatform.discovery.infrastructure.persistence;

import com.storyplatform.discovery.application.port.RankingRepository;

import java.time.Instant;
import java.util.List;

public final class DisabledRankingRepository implements RankingRepository {

    @Override
    public List<RankedSubject> stories(
            Instant from,
            Instant to,
            Metric metric,
            int limit
    ) {
        return List.of();
    }

    @Override
    public List<RankedSubject> teams(
            Instant from,
            Instant to,
            Metric metric,
            int limit
    ) {
        return List.of();
    }
}
