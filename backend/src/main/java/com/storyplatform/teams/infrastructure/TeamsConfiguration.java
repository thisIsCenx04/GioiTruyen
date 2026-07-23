package com.storyplatform.teams.infrastructure;

import com.storyplatform.identity.application.contract.IdentityUserDirectory;
import com.storyplatform.teams.application.ProfileService;
import com.storyplatform.teams.application.TeamOperations;
import com.storyplatform.teams.application.TeamUseCase;
import com.storyplatform.teams.application.port.TeamMembershipRepository;
import com.storyplatform.teams.application.port.TeamRepository;
import com.storyplatform.teams.application.port.UserProfileRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
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
}
