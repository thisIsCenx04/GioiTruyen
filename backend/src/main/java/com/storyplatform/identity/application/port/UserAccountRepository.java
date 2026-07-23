package com.storyplatform.identity.application.port;

import com.storyplatform.identity.domain.UserAccount;

public interface UserAccountRepository {

    boolean saveIfEmailAvailable(UserAccount account);
}
