package com.storyplatform.identity.application.port;

@FunctionalInterface
public interface PasswordHasher {

    String hash(String rawPassword);

    default boolean matches(
            String rawPassword,
            String encodedPassword
    ) {
        throw new UnsupportedOperationException(
                "Password verification is not implemented"
        );
    }
}
