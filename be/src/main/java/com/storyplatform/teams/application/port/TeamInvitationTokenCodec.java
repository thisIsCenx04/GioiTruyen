package com.storyplatform.teams.application.port;

import java.time.Instant;

public interface TeamInvitationTokenCodec {

    IssuedToken issue(String userId, Instant expiresAt);

    String hash(String rawToken);

    boolean isWellFormed(String rawToken);

    record IssuedToken(String invitationId, String tokenHash) {
    }
}
