package com.storyplatform.monetization.application.port;

public interface WithdrawalPayoutCallbackVerifier {

    boolean verify(
            String provider,
            byte[] rawBody,
            String timestamp,
            String signature
    );
}
