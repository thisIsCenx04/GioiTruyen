package com.storyplatform.community.infrastructure;

import com.storyplatform.community.application.port.StoryRelationRepository;
import com.storyplatform.community.domain.StoryRelation;

import java.util.Objects;

public final class StoryRelationCounterReconciler {

    private final StoryRelationRepository relations;
    private final StoryRelationCounterStore counters;

    public StoryRelationCounterReconciler(
            StoryRelationRepository relations,
            StoryRelationCounterStore counters
    ) {
        this.relations = Objects.requireNonNull(relations);
        this.counters = Objects.requireNonNull(counters);
    }

    public Counts reconcile(String storyId) {
        long favorites = relations.count(
                storyId,
                StoryRelation.Type.FAVORITE
        );
        long followers = relations.count(
                storyId,
                StoryRelation.Type.FOLLOW
        );
        counters.reconcile(storyId, favorites, followers);
        return new Counts(favorites, followers);
    }

    public record Counts(long favorites, long followers) {
    }
}
