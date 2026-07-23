package com.storyplatform.identity.application.port;

public interface ReauthenticationTokenCodec {

    IssuedToken issue();

    String hash(String rawToken);

    boolean isWellFormed(String rawToken);

    record IssuedToken(String value, String hash) {
    }
}
