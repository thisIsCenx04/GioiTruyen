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
        RATE_LIMITED,
        MFA_CODE_REQUIRED,
        MFA_ENROLLMENT_REQUIRED
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

    public static LoginOutcome mfaCodeRequired() {
        return empty(Status.MFA_CODE_REQUIRED);
    }

    public static LoginOutcome mfaEnrollmentRequired() {
        return empty(Status.MFA_ENROLLMENT_REQUIRED);
    }

    private static LoginOutcome empty(Status status) {
        return new LoginOutcome(status, null, 0, null, 0);
    }
}
