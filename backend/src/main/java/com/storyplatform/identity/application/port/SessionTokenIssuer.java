package com.storyplatform.identity.application.port;

import com.storyplatform.identity.domain.UserAccount;

public interface SessionTokenIssuer {

    IssuedSession issue(UserAccount account);

    record IssuedSession(
            String accessToken,
            long accessTokenExpiresInSeconds,
            String refreshToken
    ) {
    }
}
