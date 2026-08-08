package com.storyplatform.teams.application;

public final class ProfileNotFoundException extends RuntimeException {

    public ProfileNotFoundException() {
        super("The requested profile does not exist.");
    }
}
