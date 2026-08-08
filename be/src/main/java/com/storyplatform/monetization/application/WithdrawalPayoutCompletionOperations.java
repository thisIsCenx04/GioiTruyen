package com.storyplatform.monetization.application;

import com.storyplatform.monetization.application.port
        .WithdrawalPayoutGateway;

public interface WithdrawalPayoutCompletionOperations {

    void complete(
            WithdrawalPayoutClaimOperations.Claim claim,
            WithdrawalPayoutGateway.Result result
    );

    void retry(
            WithdrawalPayoutClaimOperations.Claim claim,
            String errorCode
    );
}
