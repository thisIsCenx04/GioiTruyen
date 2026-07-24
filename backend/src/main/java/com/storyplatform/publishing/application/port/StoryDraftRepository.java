package com.storyplatform.publishing.application.port;

import com.storyplatform.publishing.domain.StoryDraft;
import com.storyplatform.publishing.domain.StoryRevision;

import java.util.Optional;

public interface StoryDraftRepository {

    Optional<StoredDraft> findReplay(String teamId, String idempotencyKey);

    void insert(
            StoryDraft story,
            StoryRevision revision,
            String createdBy,
            String idempotencyKey,
            String idempotencyFingerprint
    );

    record StoredDraft(
            StoryDraft story,
            long revisionNo,
            String idempotencyFingerprint
    ) {
    }
}
