package com.storyplatform.teams.application;

public interface TeamFollowOperations {

    FollowView status(String actorId, String teamId);

    FollowView follow(String actorId, String teamId);

    FollowView unfollow(String actorId, String teamId);

    record FollowView(
            String teamId,
            boolean following,
            long followerCount
    ) {
    }
}
