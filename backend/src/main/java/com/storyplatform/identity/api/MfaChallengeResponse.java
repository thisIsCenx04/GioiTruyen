package com.storyplatform.identity.api;

public record MfaChallengeResponse(
        String provisioningSecret,
        String algorithm,
        int digits,
        int periodSeconds
) {
}
