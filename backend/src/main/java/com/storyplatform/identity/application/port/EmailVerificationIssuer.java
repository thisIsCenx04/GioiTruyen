package com.storyplatform.identity.application.port;

import java.time.Instant;

public interface EmailVerificationIssuer {

    void issue(String userId, String correlationId, Instant issuedAt);
}
