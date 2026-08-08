package com.storyplatform.notifications.application;

public final class NotificationProviderException extends RuntimeException {

    private final String code;
    private final boolean retryable;

    public NotificationProviderException(
            String code,
            String message,
            boolean retryable
    ) {
        super(message);
        this.code = code;
        this.retryable = retryable;
    }

    public String code() {
        return code;
    }

    public boolean retryable() {
        return retryable;
    }
}
