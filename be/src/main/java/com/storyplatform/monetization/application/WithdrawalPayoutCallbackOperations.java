package com.storyplatform.monetization.application;

public interface WithdrawalPayoutCallbackOperations {

    void accept(
            String provider,
            byte[] rawBody,
            String timestamp,
            String signature
    );
}
