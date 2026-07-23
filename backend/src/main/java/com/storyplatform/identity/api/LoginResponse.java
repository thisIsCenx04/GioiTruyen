package com.storyplatform.identity.api;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        String refreshToken
) {
    static LoginResponse bearer(
            String token,
            long expiresIn,
            String refreshToken
    ) {
        return new LoginResponse(
                token,
                "Bearer",
                expiresIn,
                refreshToken
        );
    }
}
