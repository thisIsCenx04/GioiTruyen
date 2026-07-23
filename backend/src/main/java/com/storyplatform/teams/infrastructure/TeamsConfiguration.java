package com.storyplatform.teams.infrastructure;

import com.storyplatform.identity.application.contract.IdentityUserDirectory;
import com.storyplatform.shared.events.persistence.OutboxAppender;
import com.storyplatform.teams.application.ProfileService;
import com.storyplatform.teams.application.TeamMembershipOperations;
import com.storyplatform.teams.application.TeamMembershipUseCase;
import com.storyplatform.teams.application.TeamOperations;
import com.storyplatform.teams.application.TeamUseCase;
import com.storyplatform.teams.application.port.TeamInvitationRepository;
import com.storyplatform.teams.application.port.TeamInvitationTokenCodec;
import com.storyplatform.teams.application.port.TeamMembershipRepository;
import com.storyplatform.teams.application.port.TeamRepository;
import com.storyplatform.teams.application.port.UserProfileRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

@Configuration(proxyBeanMethods = false)
public class TeamsConfiguration {

    @Bean
    ProfileService profileService(
            IdentityUserDirectory identities,
            UserProfileRepository profiles
    ) {
        return new ProfileService(
                identities,
                profiles,
                Clock.systemUTC()
        );
    }

    @Bean
    TeamOperations teamOperations(
            TeamRepository teams,
            TeamMembershipRepository memberships
    ) {
        TeamUseCase useCase = new TeamUseCase(
                teams,
                memberships,
                () -> UUID.randomUUID().toString(),
                Clock.systemUTC()
        );
        return new TransactionalTeamOperations(useCase);
    }

    @Bean
    TeamMembershipOperations teamMembershipOperations(
            TeamRepository teams,
            TeamMembershipRepository memberships,
            TeamInvitationRepository invitations,
            IdentityUserDirectory identities,
            TeamInvitationTokenCodec tokens,
            OutboxAppender outbox
    ) {
        TeamMembershipUseCase useCase = new TeamMembershipUseCase(
                teams,
                memberships,
                invitations,
                identities,
                tokens,
                outbox,
                Duration.ofDays(7),
                Clock.systemUTC()
        );
        return new TransactionalTeamMembershipOperations(useCase);
    }
}
