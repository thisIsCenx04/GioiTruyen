package com.storyplatform.media.application.port;

@FunctionalInterface
public interface WebhookSignatureVerifier {

    boolean verify(byte[] body, String timestamp, String signature);
}
