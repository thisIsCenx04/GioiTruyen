package com.storyplatform.monetization.application.port;

public interface PaymentWebhookVerifier {

    boolean verify(
            String provider,
            byte[] rawBody,
            String timestamp,
            String signature
    );
}
