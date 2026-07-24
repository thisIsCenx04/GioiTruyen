package com.storyplatform.identity.application.contract;

public interface ReauthenticationVerifier {

    boolean consume(
            String rawToken,
            String actorId,
            String scope,
            String targetType,
            String targetId
    );
}
