package com.storyplatform.identity.api;

import java.time.Instant;

public record ReauthenticationGrantResponse(
        String grantToken,
        String grantType,
        long expiresIn,
        Instant expiresAt
) {
}
