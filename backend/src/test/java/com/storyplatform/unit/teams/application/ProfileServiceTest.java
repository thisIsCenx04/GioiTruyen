package com.storyplatform.unit.teams.application;

import com.storyplatform.identity.application.contract.IdentityUserDirectory;
import com.storyplatform.teams.application.ProfileService;
import com.storyplatform.teams.application.ProfileNotFoundException;
import com.storyplatform.teams.application.ProfileVersionConflictException;
import com.storyplatform.teams.application.port.UserProfileRepository;
import com.storyplatform.teams.domain.UserProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProfileServiceTest {

    private static final Instant NOW = Instant.parse(
            "2026-07-24T00:00:00Z"
    );
    private static final String USER_ID =
            "73457d55-9602-4bcd-bbf0-e38b99c6c56e";

    private final StubIdentityDirectory identities =
            new StubIdentityDirectory();
    private final StubProfileRepository profiles =
            new StubProfileRepository();
    private ProfileService service;

    @BeforeEach
    void setUp() {
        identities.user = Optional.of(identity("ACTIVE"));
        profiles.values.clear();
        service = new ProfileService(
                identities,
                profiles,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void createsAndReturnsPrivateProfileWithIdentityFields() {
        ProfileService.PrivateProfile profile =
                service.privateProfile(USER_ID);

        assertThat(profile.email()).isEqualTo("reader@example.test");
        assertThat(profile.displayName()).isEqualTo("Độc giả");
        assertThat(profile.roles()).containsExactly("USER");
        assertThat(profile.version()).isZero();
    }

    @Test
    void publicProfileNeverContainsPrivateIdentityFields() {
        ProfileService.PublicProfile profile =
                service.publicProfile(USER_ID);

        assertThat(profile.id()).isEqualTo(USER_ID);
        assertThat(profile.displayName()).isEqualTo("Độc giả");
        assertThat(profile.getClass().getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("email", "roles", "state");
    }

    @Test
    void hidesPendingAndMissingIdentities() {
        identities.user = Optional.of(identity("PENDING_EMAIL_VERIFICATION"));
        assertProfileNotFound(() -> service.publicProfile(USER_ID));

        identities.user = Optional.empty();
        assertProfileNotFound(() -> service.privateProfile(USER_ID));
    }

    @Test
    void updatesWithOptimisticVersionAndReturnsNewSnapshot() {
        service.privateProfile(USER_ID);

        ProfileService.PrivateProfile updated = service.update(
                USER_ID,
                0,
                "  Lam Dạ  ",
                "  Tác giả  ",
                null
        );

        assertThat(updated.displayName()).isEqualTo("Lam Dạ");
        assertThat(updated.bio()).isEqualTo("Tác giả");
        assertThat(updated.version()).isEqualTo(1);
    }

    @Test
    void rejectsStaleOptimisticVersion() {
        service.privateProfile(USER_ID);

        assertThatThrownBy(() -> service.update(
                USER_ID,
                9,
                "Lam Dạ",
                "",
                null
        )).isInstanceOf(ProfileVersionConflictException.class);
    }

    private static void assertProfileNotFound(Runnable operation) {
        assertThatThrownBy(operation::run)
                .isInstanceOf(ProfileNotFoundException.class);
    }

    private static IdentityUserDirectory.IdentityUser identity(String state) {
        return new IdentityUserDirectory.IdentityUser(
                USER_ID,
                "reader@example.test",
                Set.of("USER"),
                state,
                NOW.minusSeconds(3600)
        );
    }

    private static final class StubIdentityDirectory
            implements IdentityUserDirectory {

        private Optional<IdentityUser> user = Optional.empty();

        @Override
        public Optional<IdentityUser> findById(String userId) {
            return user.filter(candidate -> candidate.id().equals(userId));
        }
    }

    private static final class StubProfileRepository
            implements UserProfileRepository {

        private final Map<String, UserProfile> values = new HashMap<>();

        @Override
        public UserProfile findOrCreate(
                String userId,
                String defaultDisplayName,
                Instant now
        ) {
            return values.computeIfAbsent(userId, ignored -> new UserProfile(
                    userId,
                    defaultDisplayName,
                    "",
                    null,
                    now,
                    now,
                    0
            ));
        }

        @Override
        public Optional<UserProfile> findByUserId(String userId) {
            return Optional.ofNullable(values.get(userId));
        }

        @Override
        public UpdateResult update(
                String userId,
                long expectedVersion,
                String displayName,
                String bio,
                String avatarMediaId,
                Instant now
        ) {
            UserProfile current = values.get(userId);
            if (current == null) {
                return UpdateResult.NOT_FOUND;
            }
            if (current.version() != expectedVersion) {
                return UpdateResult.VERSION_CONFLICT;
            }
            values.put(userId, new UserProfile(
                    userId,
                    displayName,
                    bio,
                    avatarMediaId,
                    current.createdAt(),
                    now,
                    current.version() + 1
            ));
            return UpdateResult.UPDATED;
        }
    }
}
