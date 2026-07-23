package com.storyplatform.teams.application;

public final class ProfileVersionConflictException extends RuntimeException {

    public ProfileVersionConflictException() {
        super("The profile changed. Reload it before trying again.");
    }
}
