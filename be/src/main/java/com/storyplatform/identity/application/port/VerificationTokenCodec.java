package com.storyplatform.identity.application.port;

import java.time.Instant;

public interface VerificationTokenCodec {

    IssuedVerificationToken issue(String userId, Instant expiresAt);

    String tokenForDelivery(
            String verificationId,
            String userId,
            Instant expiresAt
    );

    String hash(String rawToken);

    boolean isWellFormed(String rawToken);

    record IssuedVerificationToken(
            String verificationId,
            String tokenHash
    ) {
    }
}
