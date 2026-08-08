package com.storyplatform.teams.application.contract;

@FunctionalInterface
public interface TeamStatusDirectory {

    boolean isActive(String teamId);
}
