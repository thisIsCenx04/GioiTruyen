package com.storyplatform.identity.application.port;

@FunctionalInterface
public interface PasswordHasher {

    String hash(String rawPassword);
}
