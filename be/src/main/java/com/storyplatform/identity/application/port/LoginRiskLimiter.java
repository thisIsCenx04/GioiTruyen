package com.storyplatform.identity.application.port;

public interface LoginRiskLimiter {

    boolean allow(String emailFingerprint, String addressFingerprint);

    long retryAfterSeconds();

    void recordFailure(
            String emailFingerprint,
            String addressFingerprint
    );

    void recordSuccess(
            String emailFingerprint,
            String addressFingerprint
    );
}
