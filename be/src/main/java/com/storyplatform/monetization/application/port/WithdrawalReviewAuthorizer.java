package com.storyplatform.monetization.application.port;

public interface WithdrawalReviewAuthorizer {

    boolean consume(
            String actorId,
            String rawToken,
            String withdrawalId
    );
}
