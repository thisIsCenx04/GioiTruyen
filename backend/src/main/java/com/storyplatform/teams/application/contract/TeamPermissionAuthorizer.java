package com.storyplatform.teams.application.contract;

@FunctionalInterface
public interface TeamPermissionAuthorizer {

    boolean allows(String userId, String teamId, String permission);
}
