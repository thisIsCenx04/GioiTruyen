package com.storyplatform.identity.application.port;

import com.storyplatform.identity.domain.UserAccount;

import java.time.Instant;
import java.util.Optional;

public interface UserAccountRepository {

    boolean saveIfEmailAvailable(UserAccount account);

    boolean activatePending(String userId, Instant activatedAt);

    default boolean resetPassword(
            String userId,
            String passwordHash,
            Instant changedAt
    ) {
        return false;
    }

    default boolean incrementSecurityVersion(
            String userId,
            Instant changedAt
    ) {
        return false;
    }

    default Optional<UserAccount> findByEmail(String emailNormalized) {
        return Optional.empty();
    }

    default Optional<UserAccount> findById(String userId) {
        return Optional.empty();
    }
}
