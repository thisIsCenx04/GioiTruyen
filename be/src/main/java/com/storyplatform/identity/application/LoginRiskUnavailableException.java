package com.storyplatform.identity.application;

public final class LoginRiskUnavailableException extends RuntimeException {

    public LoginRiskUnavailableException(Throwable cause) {
        super("Login risk controls are unavailable", cause);
    }
}
