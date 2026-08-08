package com.storyplatform.identity.infrastructure.security;

import com.storyplatform.identity.application.port.PasswordHasher;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

import java.util.Objects;

public final class Argon2PasswordHasher implements PasswordHasher {

    private static final int SALT_LENGTH = 16;
    private static final int HASH_LENGTH = 32;

    private final Argon2PasswordEncoder encoder;

    public Argon2PasswordHasher(
            int memoryKb,
            int iterations,
            int parallelism
    ) {
        this.encoder = new Argon2PasswordEncoder(
                SALT_LENGTH,
                HASH_LENGTH,
                parallelism,
                memoryKb,
                iterations
        );
    }

    @Override
    public String hash(String rawPassword) {
        Objects.requireNonNull(rawPassword, "rawPassword");
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(
            String rawPassword,
            String encodedPassword
    ) {
        Objects.requireNonNull(rawPassword, "rawPassword");
        Objects.requireNonNull(encodedPassword, "encodedPassword");
        return encoder.matches(rawPassword, encodedPassword);
    }
}
