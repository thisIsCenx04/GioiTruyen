package com.storyplatform.teams.infrastructure;

import com.storyplatform.identity.application.contract.IdentityUserDirectory;
import com.storyplatform.moderation.application.contract
        .ExternalDonationContentPolicy;
import com.storyplatform.shared.events.persistence.OutboxAppender;
import com.storyplatform.shared.cache.RedisKeyFactory;
import com.storyplatform.shared.cache.ResilientRedisCache;
import com.storyplatform.teams.application.ProfileService;
import com.storyplatform.teams.application.TeamAuthorizationPolicy;
import com.storyplatform.teams.application.TeamAuthorizationUseCase;
import com.storyplatform.teams.application.TeamFollowOperations;
import com.storyplatform.teams.application.TeamFollowUseCase;
import com.storyplatform.teams.application.TeamMembershipOperations;
import com.storyplatform.teams.application.TeamMembershipUseCase;
import com.storyplatform.teams.application.TeamOperations;
import com.storyplatform.teams.application.TeamUseCase;
import com.storyplatform.teams.application.contract.TeamStatusDirectory;
import com.storyplatform.teams.application.port.TeamInvitationRepository;
import com.storyplatform.teams.application.port.TeamInvitationTokenCodec;
import com.storyplatform.teams.application.port.TeamMembershipRepository;
import com.storyplatform.teams.application.port.TeamMembershipCache;
import com.storyplatform.teams.application.port.TeamFollowRepository;
import com.storyplatform.teams.application.port.TeamRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import com.storyplatform.teams.application.port.UserProfileRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

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
            TeamMembershipRepository memberships,
            ExternalDonationContentPolicy donationPolicy
    ) {
        TeamUseCase useCase = new TeamUseCase(
                teams,
                memberships,
                () -> UUID.randomUUID().toString(),
                Clock.systemUTC(),
                donationPolicy
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

    @Bean
    TeamMembershipCache teamMembershipCache(
            ResilientRedisCache cache,
            RedisKeyFactory keys
    ) {
        return new RedisTeamMembershipCache(cache, keys);
    }

    @Bean
    TeamAuthorizationPolicy teamAuthorizationPolicy(
            TeamMembershipRepository memberships,
            TeamMembershipCache cache
    ) {
        return new TeamAuthorizationUseCase(memberships, cache);
    }

    @Bean
    TeamStatusDirectory teamStatusDirectory(TeamRepository teams) {
        return teamId -> teams.findById(teamId)
                .filter(team -> team.state() ==
                        com.storyplatform.teams.domain.Team.State.ACTIVE)
                .isPresent();
    }

    @Bean
    TeamFollowOperations teamFollowOperations(
            TeamRepository teams,
            TeamFollowRepository follows,
            OutboxAppender outbox
    ) {
        TeamFollowUseCase useCase = new TeamFollowUseCase(
                teams,
                follows,
                outbox,
                Clock.systemUTC()
        );
        return new TransactionalTeamFollowOperations(useCase);
    }

    @Bean
    TeamFollowCounterStore teamFollowCounterStore(
            JdbcClient jdbc
    ) {
        return new TeamFollowCounterStore(jdbc, Clock.systemUTC());
    }

    @Bean
    TeamFollowCounterProjector teamFollowCounterProjector(
            TeamFollowCounterStore counters,
            ObjectMapper objectMapper
    ) {
        return new TeamFollowCounterProjector(counters, objectMapper);
    }

    @Bean
    TeamFollowCounterReconciler teamFollowCounterReconciler(
            TeamFollowRepository follows,
            TeamFollowCounterStore counters
    ) {
        return new TeamFollowCounterReconciler(follows, counters);
    }
}
