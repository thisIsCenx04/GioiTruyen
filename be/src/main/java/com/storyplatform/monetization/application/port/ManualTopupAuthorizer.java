package com.storyplatform.monetization.application.port;

public interface ManualTopupAuthorizer {

    boolean consume(
            String actorId,
            String rawToken,
            String topupRequestId
    );
}
