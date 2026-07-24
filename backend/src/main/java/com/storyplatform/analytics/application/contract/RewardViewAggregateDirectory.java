package com.storyplatform.analytics.application.contract;

import java.time.Instant;
import java.util.List;

/**
 * Published, read-only snapshot source for monetization reward periods.
 */
public interface RewardViewAggregateDirectory {

    String aggregateVersion();

    List<TeamValidViews> validViewsByTeam(Instant from, Instant to);

    record TeamValidViews(String teamId, long validViews) {
        public TeamValidViews {
            if (teamId == null || teamId.isBlank() || validViews < 0) {
                throw new IllegalArgumentException(
                        "Reward view aggregate is invalid."
                );
            }
        }
    }
}
