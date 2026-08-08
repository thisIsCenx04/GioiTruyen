package com.storyplatform.identity.application.port;

@FunctionalInterface
public interface UserIdGenerator {

    String nextId();
}
