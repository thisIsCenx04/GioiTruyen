package com.storyplatform.identity.application.contract;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

public interface IdentityUserDirectory {

    Optional<IdentityUser> findById(String userId);

    record IdentityUser(
            String id,
            String email,
            Set<String> roles,
            String state,
            Instant createdAt
    ) {
        public IdentityUser {
            roles = Set.copyOf(roles);
        }

        public boolean active() {
            return "ACTIVE".equals(state);
        }
    }
}
