package com.storyplatform.teams.application;

public final class TeamConflictException extends RuntimeException {

    private final String code;

    public TeamConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
