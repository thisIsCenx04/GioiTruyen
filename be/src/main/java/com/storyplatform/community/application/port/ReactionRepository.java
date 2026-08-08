package com.storyplatform.community.application.port;

import com.storyplatform.community.domain.Reaction;

public interface ReactionRepository {

    boolean targetIsVisible(
            Reaction.TargetType targetType,
            String targetId
    );

    boolean insertIfAbsent(Reaction reaction);

    boolean deleteIfPresent(
            Reaction.TargetType targetType,
            String targetId,
            String actorId
    );

    boolean exists(
            Reaction.TargetType targetType,
            String targetId,
            String actorId
    );

    long count(Reaction.TargetType targetType, String targetId);
}
