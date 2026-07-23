package com.storyplatform.identity.domain;

import java.util.Locale;
import java.util.Set;

public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 128;

    private static final Set<String> BLOCKED_PASSWORDS = Set.of(
            "password1234",
            "password123!",
            "123456789012",
            "qwertyuiop12",
            "gioitruyen123"
    );

    public boolean accepts(String password) {
        if (password == null) {
            return false;
        }
        int codePoints = password.codePointCount(0, password.length());
        if (codePoints < MIN_LENGTH || codePoints > MAX_LENGTH) {
            return false;
        }
        if (password.codePoints().anyMatch(Character::isISOControl)) {
            return false;
        }
        return !BLOCKED_PASSWORDS.contains(
                password.toLowerCase(Locale.ROOT)
        );
    }
}
