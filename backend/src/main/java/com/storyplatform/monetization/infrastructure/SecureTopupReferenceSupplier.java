package com.storyplatform.monetization.infrastructure;

import java.security.SecureRandom;
import java.util.Objects;
import java.util.function.Supplier;

public final class SecureTopupReferenceSupplier
        implements Supplier<String> {

    private static final char[] ALPHABET =
            "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private final SecureRandom random;

    public SecureTopupReferenceSupplier(SecureRandom random) {
        this.random = Objects.requireNonNull(random);
    }

    @Override
    public String get() {
        char[] value = new char[16];
        value[0] = 'G';
        value[1] = 'T';
        for (int index = 2; index < value.length; index++) {
            value[index] = ALPHABET[random.nextInt(ALPHABET.length)];
        }
        return new String(value);
    }
}
