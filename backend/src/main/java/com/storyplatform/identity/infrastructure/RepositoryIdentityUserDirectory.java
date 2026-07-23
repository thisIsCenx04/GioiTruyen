package com.storyplatform.identity.infrastructure;

import com.storyplatform.identity.application.contract.IdentityUserDirectory;
import com.storyplatform.identity.application.port.UserAccountRepository;
import com.storyplatform.identity.domain.UserAccount;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.stream.Collectors;

@Component
public final class RepositoryIdentityUserDirectory
        implements IdentityUserDirectory {

    private final UserAccountRepository users;

    public RepositoryIdentityUserDirectory(
            UserAccountRepository users
    ) {
        this.users = Objects.requireNonNull(users, "users");
    }

    @Override
    public java.util.Optional<IdentityUser> findById(String userId) {
        return users.findById(userId).map(
                RepositoryIdentityUserDirectory::toView
        );
    }

    private static IdentityUser toView(UserAccount account) {
        return new IdentityUser(
                account.id(),
                account.emailNormalized(),
                account.globalRoles().stream()
                        .map(Enum::name)
                        .collect(Collectors.toUnmodifiableSet()),
                account.state().name(),
                account.createdAt()
        );
    }
}
