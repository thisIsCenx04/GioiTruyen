package com.storyplatform.monetization.application;

public interface PaymentWebhookOperations {

    Result accept(
            String provider,
            byte[] rawBody,
            String timestamp,
            String signature
    );

    enum Result {
        ACCEPTED,
        DUPLICATE
    }
}
