package com.storyplatform.community.infrastructure;

/**
 * Port for updating the denormalized story-relation counter cache.
 * Implementations are expected to be idempotent.
 */
public interface StoryRelationCounterStore {

    /**
     * Reconciles the cached favorite and follower counts for the given story
     * to the supplied authoritative values.
     *
     * @param storyId   the story identifier
     * @param favorites the exact number of active favorites
     * @param followers the exact number of active followers
     */
    void reconcile(String storyId, long favorites, long followers);
}
