package com.storyplatform.identity.application.port;

import com.storyplatform.identity.domain.UserAccount;

public interface AccessTokenIssuer {

    IssuedAccessToken issue(UserAccount account, String sessionId);

    record IssuedAccessToken(String value, long expiresInSeconds) {
    }
}
