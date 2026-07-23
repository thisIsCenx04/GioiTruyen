package com.storyplatform.identity.application.port;

import com.storyplatform.identity.domain.UserAccount;

import java.time.Instant;

public interface UserAccountRepository {

    boolean saveIfEmailAvailable(UserAccount account);

    boolean activatePending(String userId, Instant activatedAt);
}
