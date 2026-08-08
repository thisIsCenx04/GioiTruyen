package com.storyplatform.teams.application;

public final class TeamAccessDeniedException extends RuntimeException {

    public TeamAccessDeniedException() {
        super("The requested Team operation is not permitted.");
    }
}
