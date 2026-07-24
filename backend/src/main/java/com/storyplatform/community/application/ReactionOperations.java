package com.storyplatform.community.application;

import com.storyplatform.community.domain.Reaction;

public interface ReactionOperations {

    ReactionView status(
            String actorId,
            Reaction.TargetType targetType,
            String targetId
    );

    ReactionView add(
            String actorId,
            Reaction.TargetType targetType,
            String targetId
    );

    ReactionView remove(
            String actorId,
            Reaction.TargetType targetType,
            String targetId
    );

    record ReactionView(
            Reaction.TargetType targetType,
            String targetId,
            boolean active,
            long count
    ) {
    }
}
