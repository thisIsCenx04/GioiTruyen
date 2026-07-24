package com.storyplatform.publishing.application;

public interface PublishingPropagationOperations {

    boolean processNext(String workerId);
}
