package com.storyplatform.community.application;

import com.storyplatform.community.application.port.StoryRelationRepository;
import com.storyplatform.community.domain.StoryRelation;
import com.storyplatform.shared.events.IntegrationEvent;
import com.storyplatform.shared.events.persistence.OutboxAppender;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class StoryRelationService
        implements StoryRelationOperations {

    public static final String EVENT_TYPE =
            "community.storyrelation.changed";
    private final StoryRelationRepository relations;
    private final OutboxAppender outbox;
    private final Clock clock;

    public StoryRelationService(
            StoryRelationRepository relations,
            OutboxAppender outbox,
            Clock clock
    ) {
        this.relations = Objects.requireNonNull(relations);
        this.outbox = Objects.requireNonNull(outbox);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public RelationView status(
            String userId,
            String storyId,
            StoryRelation.Type type
    ) {
        requireStory(storyId);
        return view(userId, storyId, type);
    }

    @Override
    public RelationView add(
            String userId,
            String storyId,
            StoryRelation.Type type
    ) {
        requireStory(storyId);
        Instant now = clock.instant();
        if (relations.insertIfAbsent(
                StoryRelation.create(storyId, userId, type, now)
        )) {
            append(userId, storyId, type, 1, now);
        }
        return new RelationView(
                storyId,
                type.name(),
                true,
                relations.count(storyId, type)
        );
    }

    @Override
    public RelationView remove(
            String userId,
            String storyId,
            StoryRelation.Type type
    ) {
        requireStory(storyId);
        Instant now = clock.instant();
        if (relations.deleteIfPresent(storyId, userId, type)) {
            append(userId, storyId, type, -1, now);
        }
        return new RelationView(
                storyId,
                type.name(),
                false,
                relations.count(storyId, type)
        );
    }

    private RelationView view(
            String userId,
            String storyId,
            StoryRelation.Type type
    ) {
        return new RelationView(
                storyId,
                type.name(),
                relations.exists(storyId, userId, type),
                relations.count(storyId, type)
        );
    }

    private void requireStory(String storyId) {
        if (!relations.storyIsPublished(storyId)) {
            throw new StoryNotFoundException();
        }
    }

    private void append(
            String userId,
            String storyId,
            StoryRelation.Type type,
            int delta,
            Instant now
    ) {
        outbox.append(new IntegrationEvent(
                UUID.randomUUID(),
                EVENT_TYPE,
                1,
                now,
                UUID.randomUUID().toString(),
                "story_relation",
                storyId + ":" + userId + ":" + type.name(),
                userId,
                null,
                new RelationChanged(storyId, type.name(), delta)
        ));
    }

    public record RelationChanged(
            String storyId,
            String type,
            int delta
    ) {
        public RelationChanged {
            StoryRelation.Type.valueOf(type);
            if (delta != 1 && delta != -1) {
                throw new IllegalArgumentException(
                        "relation delta must be 1 or -1"
                );
            }
        }
    }
}
