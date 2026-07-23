package com.storyplatform.teams.application;

import com.storyplatform.teams.application.port.TeamMembershipRepository;
import com.storyplatform.teams.application.port.TeamRepository;
import com.storyplatform.teams.domain.Team;
import com.storyplatform.teams.domain.TeamMembership;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public final class TeamUseCase implements TeamOperations {

    private final TeamRepository teams;
    private final TeamMembershipRepository memberships;
    private final Supplier<String> ids;
    private final Clock clock;

    public TeamUseCase(
            TeamRepository teams,
            TeamMembershipRepository memberships,
            Supplier<String> ids,
            Clock clock
    ) {
        this.teams = Objects.requireNonNull(teams, "teams");
        this.memberships = Objects.requireNonNull(
                memberships,
                "memberships"
        );
        this.ids = Objects.requireNonNull(ids, "ids");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public TeamView create(
            String actorId,
            String slug,
            String name,
            String description
    ) {
        Instant now = clock.instant();
        Team team = new Team(
                ids.get(),
                slug,
                name.strip(),
                text(description),
                actorId,
                Team.State.ACTIVE,
                now,
                now,
                0
        );
        if (!teams.insertIfSlugAvailable(team)) {
            throw new TeamConflictException(
                    "TEAM_SLUG_TAKEN",
                    "The team slug is already in use."
            );
        }
        memberships.insertOwner(TeamMembership.owner(
                team.id(),
                actorId,
                now
        ));
        return view(team);
    }

    @Override
    public TeamView get(String teamId) {
        return view(requireTeam(teamId));
    }

    @Override
    public List<TeamView> list(int limit) {
        return teams.listActive(limit).stream()
                .map(TeamUseCase::view)
                .toList();
    }

    @Override
    public TeamView update(
            String actorId,
            String teamId,
            long version,
            String name,
            String description
    ) {
        TeamRepository.UpdateResult result = teams.updateOwned(
                teamId,
                actorId,
                version,
                name.strip(),
                text(description),
                clock.instant()
        );
        if (result == TeamRepository.UpdateResult.VERSION_CONFLICT) {
            throw new TeamConflictException(
                    "TEAM_VERSION_CONFLICT",
                    "The team changed. Reload it before trying again."
            );
        }
        if (result == TeamRepository.UpdateResult.NOT_OWNED_OR_NOT_FOUND) {
            throw new TeamNotFoundException();
        }
        return view(requireTeam(teamId));
    }

    private Team requireTeam(String teamId) {
        return teams.findById(teamId)
                .filter(team -> team.state() == Team.State.ACTIVE)
                .orElseThrow(TeamNotFoundException::new);
    }

    private static String text(String value) {
        return value == null ? "" : value.strip();
    }

    private static TeamView view(Team team) {
        return new TeamView(
                team.id(),
                team.slug(),
                team.name(),
                team.description(),
                team.state().name(),
                team.version()
        );
    }
}
