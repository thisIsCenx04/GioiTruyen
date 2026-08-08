package com.storyplatform.monetization.application;

public final class PaymentWebhookException extends RuntimeException {

    private final Kind kind;

    public PaymentWebhookException(String message, Kind kind) {
        super(message);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    public enum Kind {
        INVALID,
        UNAUTHORIZED
    }
}
