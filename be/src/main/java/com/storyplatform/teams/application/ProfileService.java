package com.storyplatform.teams.application;

import com.storyplatform.identity.application.contract.IdentityUserDirectory;
import com.storyplatform.identity.application.contract.IdentityUserDirectory.IdentityUser;
import com.storyplatform.teams.application.port.UserProfileRepository;
import com.storyplatform.teams.domain.UserProfile;

import java.time.Clock;
import java.util.Objects;

public final class ProfileService {

    private final IdentityUserDirectory identities;
    private final UserProfileRepository profiles;
    private final Clock clock;

    public ProfileService(
            IdentityUserDirectory identities,
            UserProfileRepository profiles,
            Clock clock
    ) {
        this.identities = Objects.requireNonNull(identities, "identities");
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public PrivateProfile privateProfile(String actorId) {
        IdentityUser identity = requireIdentity(actorId);
        UserProfile profile = profiles.findOrCreate(
                actorId,
                "Độc giả",
                clock.instant()
        );
        return PrivateProfile.from(identity, profile);
    }

    public PublicProfile publicProfile(String userId) {
        IdentityUser identity = requireIdentity(userId);
        if (!identity.active()) {
            throw notFound();
        }
        return profiles.findByUserId(userId)
                .map(PublicProfile::from)
                .orElseGet(() -> new PublicProfile(
                        userId,
                        "Độc giả",
                        "",
                        null,
                        0
                ));
    }

    public PrivateProfile update(
            String actorId,
            long version,
            String displayName,
            String bio,
            String avatarMediaId
    ) {
        IdentityUser identity = requireIdentity(actorId);
        profiles.findOrCreate(actorId, "Độc giả", clock.instant());
        UserProfileRepository.UpdateResult result = profiles.update(
                actorId,
                version,
                displayName.strip(),
                bio == null ? "" : bio.strip(),
                avatarMediaId,
                clock.instant()
        );
        if (result == UserProfileRepository.UpdateResult.VERSION_CONFLICT) {
            throw new ProfileVersionConflictException();
        }
        if (result == UserProfileRepository.UpdateResult.NOT_FOUND) {
            throw notFound();
        }
        UserProfile updated = profiles.findByUserId(actorId)
                .orElseThrow(ProfileService::notFound);
        return PrivateProfile.from(identity, updated);
    }

    private IdentityUser requireIdentity(String userId) {
        return identities.findById(userId).orElseThrow(ProfileService::notFound);
    }

    private static ProfileNotFoundException notFound() {
        return new ProfileNotFoundException();
    }

    public record PublicProfile(
            String id,
            String displayName,
            String bio,
            String avatarMediaId,
            long version
    ) {
        static PublicProfile from(UserProfile profile) {
            return new PublicProfile(
                    profile.userId(),
                    profile.displayName(),
                    profile.bio(),
                    profile.avatarMediaId(),
                    profile.version()
            );
        }
    }

    public record PrivateProfile(
            String id,
            String email,
            java.util.Set<String> roles,
            String state,
            String displayName,
            String bio,
            String avatarMediaId,
            long version,
            java.time.Instant createdAt
    ) {
        static PrivateProfile from(
                IdentityUser identity,
                UserProfile profile
        ) {
            return new PrivateProfile(
                    identity.id(),
                    identity.email(),
                    identity.roles(),
                    identity.state(),
                    profile.displayName(),
                    profile.bio(),
                    profile.avatarMediaId(),
                    profile.version(),
                    identity.createdAt()
            );
        }
    }
}
