package com.storyplatform.identity.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

public record UserAccount(
        String id,
        String emailNormalized,
        String passwordHash,
        Set<GlobalRole> globalRoles,
        UserState state,
        long securityVersion,
        String acceptedConsentVersion,
        Instant consentAcceptedAt,
        Instant createdAt,
        Instant updatedAt,
        long version
) {

    public UserAccount {
        id = requireText(id, "id");
        emailNormalized = requireText(
                emailNormalized,
                "emailNormalized"
        );
        passwordHash = requireText(passwordHash, "passwordHash");
        globalRoles = Set.copyOf(
                Objects.requireNonNull(globalRoles, "globalRoles")
        );
        state = Objects.requireNonNull(state, "state");
        acceptedConsentVersion = requireText(
                acceptedConsentVersion,
                "acceptedConsentVersion"
        );
        consentAcceptedAt = Objects.requireNonNull(
                consentAcceptedAt,
                "consentAcceptedAt"
        );
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");

        if (globalRoles.isEmpty()) {
            throw new IllegalArgumentException(
                    "globalRoles must not be empty"
            );
        }
        if (securityVersion < 1) {
            throw new IllegalArgumentException(
                    "securityVersion must be positive"
            );
        }
        if (version < 0) {
            throw new IllegalArgumentException(
                    "version must not be negative"
            );
        }
        if (updatedAt.isBefore(createdAt)
                || !consentAcceptedAt.equals(createdAt)) {
            throw new IllegalArgumentException(
                    "pending account timestamps are inconsistent"
            );
        }
    }

    public static UserAccount pending(
            String id,
            String emailNormalized,
            String passwordHash,
            String consentVersion,
            Instant now
    ) {
        return new UserAccount(
                id,
                emailNormalized,
                passwordHash,
                Set.of(GlobalRole.USER),
                UserState.PENDING_EMAIL_VERIFICATION,
                1,
                consentVersion,
                now,
                now,
                now,
                0
        );
    }

    public static UserAccount active(
            String id,
            String emailNormalized,
            String passwordHash,
            String consentVersion,
            Instant now
    ) {
        return new UserAccount(
                id,
                emailNormalized,
                passwordHash,
                Set.of(GlobalRole.USER),
                UserState.ACTIVE,
                1,
                consentVersion,
                now,
                now,
                now,
                0
        );
    }

    public boolean isPendingVerification() {
        return state == UserState.PENDING_EMAIL_VERIFICATION;
    }

    public boolean isActive() {
        return state == UserState.ACTIVE;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    field + " must not be blank"
            );
        }
        return value;
    }
}
