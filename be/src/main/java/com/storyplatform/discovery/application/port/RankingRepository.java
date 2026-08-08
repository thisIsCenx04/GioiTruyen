package com.storyplatform.discovery.application.port;

import java.time.Instant;
import java.util.List;

public interface RankingRepository {

    List<RankedSubject> stories(
            Instant from,
            Instant to,
            Metric metric,
            int limit
    );

    List<RankedSubject> teams(
            Instant from,
            Instant to,
            Metric metric,
            int limit
    );

    record RankedSubject(
            String id,
            String name,
            String teamId,
            long validViews,
            long completedViews,
            long invalidViews,
            double score
    ) {
    }

    enum Metric {
        VALID_VIEWS,
        COMPLETION_RATE,
        QUALITY_RATE
    }
}
