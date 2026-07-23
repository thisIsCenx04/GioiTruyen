package com.storyplatform.identity.application;

public record RefreshSessionOutcome(
        Status status,
        String accessToken,
        long expiresInSeconds,
        String refreshToken
) {

    public enum Status {
        ROTATED,
        INVALID,
        REUSE_DETECTED
    }

    public static RefreshSessionOutcome rotated(
            String accessToken,
            long expiresInSeconds,
            String refreshToken
    ) {
        return new RefreshSessionOutcome(
                Status.ROTATED,
                accessToken,
                expiresInSeconds,
                refreshToken
        );
    }

    public static RefreshSessionOutcome invalid() {
        return new RefreshSessionOutcome(
                Status.INVALID,
                null,
                0,
                null
        );
    }

    public static RefreshSessionOutcome reuseDetected() {
        return new RefreshSessionOutcome(
                Status.REUSE_DETECTED,
                null,
                0,
                null
        );
    }
}
