package com.storyplatform.community.application;

public final class StoryNotFoundException extends RuntimeException {

    public StoryNotFoundException() {
        super("Published story was not found.");
    }
}
