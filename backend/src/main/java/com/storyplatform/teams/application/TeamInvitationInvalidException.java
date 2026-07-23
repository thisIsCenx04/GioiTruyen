package com.storyplatform.teams.application;

public final class TeamInvitationInvalidException extends RuntimeException {

    public TeamInvitationInvalidException() {
        super("The invitation is invalid or expired.");
    }
}
