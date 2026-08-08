package com.storyplatform.community.application;

import com.storyplatform.community.application.port.ReactionRepository;
import com.storyplatform.community.domain.Reaction;
import com.storyplatform.shared.events.IntegrationEvent;
import com.storyplatform.shared.events.persistence.OutboxAppender;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class ReactionService implements ReactionOperations {

    public static final String EVENT_TYPE = "community.reaction.changed";
    private final ReactionRepository reactions;
    private final OutboxAppender outbox;
    private final Clock clock;

    public ReactionService(
            ReactionRepository reactions,
            OutboxAppender outbox,
            Clock clock
    ) {
        this.reactions = Objects.requireNonNull(reactions);
        this.outbox = Objects.requireNonNull(outbox);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public ReactionView status(
            String actorId,
            Reaction.TargetType targetType,
            String targetId
    ) {
        requireTarget(targetType, targetId);
        return view(actorId, targetType, targetId);
    }

    @Override
    public ReactionView add(
            String actorId,
            Reaction.TargetType targetType,
            String targetId
    ) {
        requireTarget(targetType, targetId);
        Instant now = clock.instant();
        if (reactions.insertIfAbsent(Reaction.create(
                targetType,
                targetId,
                actorId,
                now
        ))) {
            append(actorId, targetType, targetId, 1, now);
        }
        return new ReactionView(
                targetType,
                targetId,
                true,
                reactions.count(targetType, targetId)
        );
    }

    @Override
    public ReactionView remove(
            String actorId,
            Reaction.TargetType targetType,
            String targetId
    ) {
        requireTarget(targetType, targetId);
        Instant now = clock.instant();
        if (reactions.deleteIfPresent(targetType, targetId, actorId)) {
            append(actorId, targetType, targetId, -1, now);
        }
        return new ReactionView(
                targetType,
                targetId,
                false,
                reactions.count(targetType, targetId)
        );
    }

    private ReactionView view(
            String actorId,
            Reaction.TargetType targetType,
            String targetId
    ) {
        return new ReactionView(
                targetType,
                targetId,
                reactions.exists(targetType, targetId, actorId),
                reactions.count(targetType, targetId)
        );
    }

    private void requireTarget(
            Reaction.TargetType targetType,
            String targetId
    ) {
        if (!reactions.targetIsVisible(targetType, targetId)) {
            throw new ReactionTargetNotFoundException();
        }
    }

    private void append(
            String actorId,
            Reaction.TargetType targetType,
            String targetId,
            int delta,
            Instant now
    ) {
        outbox.append(new IntegrationEvent(
                UUID.randomUUID(),
                EVENT_TYPE,
                1,
                now,
                UUID.randomUUID().toString(),
                "reaction",
                targetType + ":" + targetId + ":" + actorId,
                actorId,
                null,
                new ReactionChanged(targetType.name(), targetId, delta)
        ));
    }

    public record ReactionChanged(
            String targetType,
            String targetId,
            int delta
    ) {
        public ReactionChanged {
            Reaction.TargetType.valueOf(targetType);
            if (delta != 1 && delta != -1) {
                throw new IllegalArgumentException(
                        "reaction delta must be 1 or -1"
                );
            }
        }
    }
}
