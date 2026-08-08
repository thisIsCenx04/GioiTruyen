package com.storyplatform.identity.application.port;

public interface RefreshTokenCodec {

    IssuedRefreshToken issue();

    String hash(String rawToken);

    boolean isWellFormed(String rawToken);

    record IssuedRefreshToken(String value, String hash) {
    }
}
