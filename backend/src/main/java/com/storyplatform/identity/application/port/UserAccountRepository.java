package com.storyplatform.identity.application.port;

import com.storyplatform.identity.domain.UserAccount;

import java.time.Instant;
import java.util.Optional;

public interface UserAccountRepository {

    boolean saveIfEmailAvailable(UserAccount account);

    boolean activatePending(String userId, Instant activatedAt);

    default Optional<UserAccount> findByEmail(String emailNormalized) {
        return Optional.empty();
    }

    default Optional<UserAccount> findById(String userId) {
        return Optional.empty();
    }
}
