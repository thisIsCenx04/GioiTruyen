package com.storyplatform.teams.application;

public final class TeamNotFoundException extends RuntimeException {

    public TeamNotFoundException() {
        super("The requested team does not exist.");
    }
}
