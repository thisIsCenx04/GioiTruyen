package com.storyplatform.unit.teams.application;

import com.storyplatform.teams.application.TeamConflictException;
import com.storyplatform.teams.application.TeamNotFoundException;
import com.storyplatform.teams.application.TeamUseCase;
import com.storyplatform.teams.application.port.TeamMembershipRepository;
import com.storyplatform.teams.application.port.TeamRepository;
import com.storyplatform.teams.domain.Team;
import com.storyplatform.teams.domain.TeamMembership;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class TeamUseCaseTest {

    private static final Instant NOW = Instant.parse(
            "2026-07-24T00:00:00Z"
    );
    private final StubTeams teams = new StubTeams();
    private final List<TeamMembership> memberships = new ArrayList<>();
    private TeamUseCase useCase;

    @BeforeEach
    void setUp() {
        teams.team = Optional.empty();
        teams.insertAllowed = true;
        teams.updateResult = TeamRepository.UpdateResult.UPDATED;
        memberships.clear();
        TeamMembershipRepository membershipRepository = mock(
                TeamMembershipRepository.class
        );
        doAnswer(invocation -> {
            memberships.add(invocation.getArgument(0));
            return null;
        }).when(membershipRepository).insertOwner(
                org.mockito.ArgumentMatchers.any()
        );
        useCase = new TeamUseCase(
                teams,
                membershipRepository,
                () -> "73457d55-9602-4bcd-bbf0-e38b99c6c56e",
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void creatorBecomesActiveOwnerWhenTeamIsCreated() {
        var created = useCase.create(
                "user-1",
                "lam-da",
                " Lâm Dạ ",
                " Nhóm dịch "
        );

        assertThat(created.slug()).isEqualTo("lam-da");
        assertThat(created.name()).isEqualTo("Lâm Dạ");
        assertThat(memberships).singleElement().satisfies(owner -> {
            assertThat(owner.teamId()).isEqualTo(created.id());
            assertThat(owner.userId()).isEqualTo("user-1");
            assertThat(owner.role()).isEqualTo(TeamMembership.Role.OWNER);
            assertThat(owner.state()).isEqualTo(TeamMembership.State.ACTIVE);
            assertThat(owner.permissions()).contains("team:manage");
        });
    }

    @Test
    void duplicateSlugCreatesNoMembership() {
        teams.insertAllowed = false;

        assertThatThrownBy(() -> useCase.create(
                "user-1",
                "lam-da",
                "Lâm Dạ",
                ""
        )).isInstanceOf(TeamConflictException.class);
        assertThat(memberships).isEmpty();
    }

    @Test
    void listsAndGetsOnlyActiveTeams() {
        teams.team = Optional.of(team(Team.State.ACTIVE, 0));
        assertThat(useCase.get(team(Team.State.ACTIVE, 0).id()).name())
                .isEqualTo("Lâm Dạ");
        assertThat(useCase.list(20)).hasSize(1);

        teams.team = Optional.of(team(Team.State.SUSPENDED, 0));
        assertThatThrownBy(() -> useCase.get("team-1"))
                .isInstanceOf(TeamNotFoundException.class);
    }

    @Test
    void appliesOwnerAndVersionPredicatesToUpdates() {
        teams.team = Optional.of(team(Team.State.ACTIVE, 2));

        var updated = useCase.update(
                "owner-1",
                "team-1",
                1,
                "Tên mới",
                null
        );
        assertThat(updated.version()).isEqualTo(2);
        assertThat(teams.lastOwner).isEqualTo("owner-1");

        teams.updateResult = TeamRepository.UpdateResult.VERSION_CONFLICT;
        assertThatThrownBy(() -> useCase.update(
                "owner-1",
                "team-1",
                1,
                "Tên mới",
                ""
        )).isInstanceOf(TeamConflictException.class);

        teams.updateResult =
                TeamRepository.UpdateResult.NOT_OWNED_OR_NOT_FOUND;
        assertThatThrownBy(() -> useCase.update(
                "other-user",
                "team-1",
                1,
                "Tên mới",
                ""
        )).isInstanceOf(TeamNotFoundException.class);
    }

    private static Team team(Team.State state, long version) {
        return new Team(
                "team-1",
                "lam-da",
                "Lâm Dạ",
                "",
                "owner-1",
                state,
                NOW,
                NOW,
                version
        );
    }

    private static final class StubTeams implements TeamRepository {

        private Optional<Team> team = Optional.empty();
        private boolean insertAllowed;
        private UpdateResult updateResult;
        private String lastOwner;

        @Override
        public boolean insertIfSlugAvailable(Team value) {
            if (insertAllowed) {
                team = Optional.of(value);
            }
            return insertAllowed;
        }

        @Override
        public Optional<Team> findById(String teamId) {
            return team.filter(value -> value.id().equals(teamId));
        }

        @Override
        public List<Team> listActive(int limit) {
            return team.filter(value -> value.state() == Team.State.ACTIVE)
                    .stream()
                    .limit(limit)
                    .toList();
        }

        @Override
        public UpdateResult updateOwned(
                String teamId,
                String ownerUserId,
                long version,
                String name,
                String description,
                Instant now
        ) {
            lastOwner = ownerUserId;
            return updateResult;
        }
    }
}
