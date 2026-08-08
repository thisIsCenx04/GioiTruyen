package com.storyplatform.discovery.application;

import java.time.Instant;
import java.util.List;

public interface RankingOperations {

    RankingPage stories(
            String period,
            String metric,
            Instant asOf,
            int limit
    );

    RankingPage teams(
            String period,
            String metric,
            Instant asOf,
            int limit
    );

    record RankingPage(
            String subject,
            String period,
            String metric,
            Instant asOf,
            boolean fallback,
            List<RankingItem> items
    ) {
        public RankingPage {
            items = List.copyOf(items);
        }
    }

    record RankingItem(
            int rank,
            String id,
            String name,
            String teamId,
            long validViews,
            long completedViews,
            long invalidViews,
            double score
    ) {
    }
}
