package com.storyplatform.community.infrastructure;

import com.storyplatform.community.application.port.StoryRelationRepository;
import com.storyplatform.community.domain.StoryRelation;

import java.util.Objects;

/**
 * Reads authoritative counts from {@link StoryRelationRepository} and writes
 * them to the denormalized {@link StoryRelationCounterStore}.
 */
public final class StoryRelationCounterReconciler {

    private final StoryRelationRepository relations;
    private final StoryRelationCounterStore counters;

    public StoryRelationCounterReconciler(
            StoryRelationRepository relations,
            StoryRelationCounterStore counters
    ) {
        this.relations = Objects.requireNonNull(relations, "relations");
        this.counters = Objects.requireNonNull(counters, "counters");
    }

    /**
     * Counts favorites and followers for the given story, reconciles the
     * counter store, and returns the result.
     */
    public ReconciliationResult reconcile(String storyId) {
        long favorites = relations.count(storyId, StoryRelation.Type.FAVORITE);
        long followers = relations.count(storyId, StoryRelation.Type.FOLLOW);
        counters.reconcile(storyId, favorites, followers);
        return new ReconciliationResult(favorites, followers);
    }

    /**
     * Snapshot of the reconciled counts.
     */
    public record ReconciliationResult(long favorites, long followers) {
    }
}
