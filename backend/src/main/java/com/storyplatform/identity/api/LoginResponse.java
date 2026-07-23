package com.storyplatform.identity.api;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {
    static LoginResponse bearer(String token, long expiresIn) {
        return new LoginResponse(token, "Bearer", expiresIn);
    }
}
