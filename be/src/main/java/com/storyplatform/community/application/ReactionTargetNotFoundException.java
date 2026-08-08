package com.storyplatform.community.application;

public final class ReactionTargetNotFoundException
        extends RuntimeException {

    public ReactionTargetNotFoundException() {
        super("The reaction target does not exist.");
    }
}
