package com.storyplatform.publishing.application.port;

import java.time.Instant;
import java.util.Optional;

public interface ContentVisibilityRepository {

    Optional<Candidate> find(String storyId);

    boolean change(
            Candidate candidate,
            String targetState,
            String actorId,
            String action,
            String reasonCode,
            String note,
            Instant changedAt
    );

    record Candidate(
            String storyId,
            String teamId,
            String state,
            String previousState,
            long version
    ) {
    }
}
