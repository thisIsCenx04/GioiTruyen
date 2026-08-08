package com.storyplatform.analytics.application.port;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface TeamAnalyticsRepository {

    List<DailyAggregate> daily(
            String teamId,
            Instant from,
            Instant to
    );

    record DailyAggregate(
            Instant start,
            long rawEvents,
            long completedViews,
            long validViews,
            long invalidViews,
            Map<String, Long> reasonCounts
    ) {
        public DailyAggregate {
            reasonCounts = Map.copyOf(reasonCounts);
        }
    }
}
