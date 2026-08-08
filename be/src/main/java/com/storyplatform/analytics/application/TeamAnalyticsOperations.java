package com.storyplatform.analytics.application;

import java.time.Instant;
import java.util.List;

public interface TeamAnalyticsOperations {

    TeamAnalyticsReport report(
            String actorId,
            String teamId,
            String period
    );

    record TeamAnalyticsReport(
            String teamId,
            String period,
            Instant from,
            Instant to,
            Totals totals,
            List<Bucket> series,
            List<Reason> reasons
    ) {
        public TeamAnalyticsReport {
            series = List.copyOf(series);
            reasons = List.copyOf(reasons);
        }
    }

    record Totals(
            long rawEvents,
            long completedViews,
            long validViews,
            long invalidViews,
            double qualityRate
    ) {
    }

    record Bucket(
            Instant start,
            long rawEvents,
            long completedViews,
            long validViews,
            long invalidViews,
            double qualityRate
    ) {
    }

    record Reason(String code, long count) {
    }
}
