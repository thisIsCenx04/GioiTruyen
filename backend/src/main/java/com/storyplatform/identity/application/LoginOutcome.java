package com.storyplatform.identity.application;

public record LoginOutcome(
        Status status,
        String accessToken,
        long expiresInSeconds,
        String refreshToken,
        long retryAfterSeconds
) {

    public enum Status {
        AUTHENTICATED,
        INVALID_CREDENTIALS,
        RATE_LIMITED
    }

    public static LoginOutcome authenticated(
            String accessToken,
            long expiresInSeconds,
            String refreshToken
    ) {
        return new LoginOutcome(
                Status.AUTHENTICATED,
                accessToken,
                expiresInSeconds,
                refreshToken,
                0
        );
    }

    public static LoginOutcome invalidCredentials() {
        return new LoginOutcome(
                Status.INVALID_CREDENTIALS,
                null,
                0,
                null,
                0
        );
    }

    public static LoginOutcome rateLimited(long retryAfterSeconds) {
        return new LoginOutcome(
                Status.RATE_LIMITED,
                null,
                0,
                null,
                retryAfterSeconds
        );
    }
}
