package com.storyplatform.teams.application;

public interface TeamAuthorizationPolicy {

    boolean allows(String userId, String teamId, String permission);
}
