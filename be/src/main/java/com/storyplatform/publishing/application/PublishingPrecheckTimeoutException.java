package com.storyplatform.publishing.application;

public final class PublishingPrecheckTimeoutException
        extends RuntimeException {

    public PublishingPrecheckTimeoutException() {
        super("Publishing precheck time budget expired");
    }
}
