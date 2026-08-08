package com.storyplatform.community.infrastructure.persistence;

import com.storyplatform.community.application.port.ReactionRepository;
import com.storyplatform.community.domain.Reaction;

public class DisabledReactionRepository implements ReactionRepository {

    @Override
    public boolean targetIsVisible(Reaction.TargetType targetType, String targetId) {
        return true;
    }

    @Override
    public boolean insertIfAbsent(Reaction reaction) {
        return true;
    }

    @Override
    public boolean deleteIfPresent(
            Reaction.TargetType targetType,
            String targetId,
            String actorId
    ) {
        return true;
    }

    @Override
    public boolean exists(
            Reaction.TargetType targetType,
            String targetId,
            String actorId
    ) {
        return false;
    }

    @Override
    public long count(Reaction.TargetType targetType, String targetId) {
        return 0L;
    }
}
