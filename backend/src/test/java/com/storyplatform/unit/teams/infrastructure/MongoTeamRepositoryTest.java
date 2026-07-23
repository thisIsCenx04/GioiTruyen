package com.storyplatform.unit.teams.infrastructure;

import com.mongodb.client.result.UpdateResult;
import com.storyplatform.teams.application.port.TeamRepository;
import com.storyplatform.teams.domain.Team;
import com.storyplatform.teams.domain.TeamMembership;
import com.storyplatform.teams.infrastructure.persistence.MongoTeamDocument;
import com.storyplatform.teams.infrastructure.persistence.MongoTeamMembershipDocument;
import com.storyplatform.teams.infrastructure.persistence.MongoTeamMembershipRepository;
import com.storyplatform.teams.infrastructure.persistence.MongoTeamRepository;
import org.bson.BsonString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MongoTeamRepositoryTest {

    private static final Instant NOW = Instant.parse(
            "2026-07-24T00:00:00Z"
    );
    private MongoTemplate mongo;
    private MongoTeamRepository teams;

    @BeforeEach
    void setUp() {
        mongo = mock(MongoTemplate.class);
        teams = new MongoTeamRepository(mongo);
    }

    @Test
    void upsertReportsWhetherSlugWasCreated() {
        when(mongo.upsert(
                any(Query.class),
                any(Update.class),
                eq(MongoTeamDocument.class)
        )).thenReturn(
                UpdateResult.acknowledged(
                        0,
                        0L,
                        new BsonString("team-1")
                ),
                UpdateResult.acknowledged(1, 0L, null)
        );

        assertThat(teams.insertIfSlugAvailable(team())).isTrue();
        assertThat(teams.insertIfSlugAvailable(team())).isFalse();
    }

    @Test
    void findsAndListsMappedTeamDocuments() {
        MongoTeamDocument document = document();
        when(mongo.findById("team-1", MongoTeamDocument.class))
                .thenReturn(document);
        when(mongo.find(any(Query.class), eq(MongoTeamDocument.class)))
                .thenReturn(List.of(document));

        assertThat(teams.findById("team-1")).get()
                .extracting(Team::slug)
                .isEqualTo("lam-da");
        assertThat(teams.listActive(20)).singleElement()
                .extracting(Team::name)
                .isEqualTo("Lâm Dạ");
    }

    @Test
    void updateDistinguishesSuccessStaleAndNonOwner() {
        when(mongo.updateFirst(
                any(Query.class),
                any(Update.class),
                eq(MongoTeamDocument.class)
        )).thenReturn(
                UpdateResult.acknowledged(1, 1L, null),
                UpdateResult.acknowledged(0, 0L, null),
                UpdateResult.acknowledged(0, 0L, null)
        );
        when(mongo.exists(
                any(Query.class),
                eq(MongoTeamDocument.class)
        )).thenReturn(true, false);

        assertThat(update()).isEqualTo(TeamRepository.UpdateResult.UPDATED);
        assertThat(update()).isEqualTo(
                TeamRepository.UpdateResult.VERSION_CONFLICT
        );
        assertThat(update()).isEqualTo(
                TeamRepository.UpdateResult.NOT_OWNED_OR_NOT_FOUND
        );
    }

    @Test
    void membershipAdapterPersistsOwnerDocument() {
        MongoTeamMembershipRepository repository =
                new MongoTeamMembershipRepository(mongo);

        repository.insertOwner(TeamMembership.owner(
                "team-1",
                "user-1",
                NOW
        ));

        verify(mongo).insert(any(MongoTeamMembershipDocument.class));
    }

    private TeamRepository.UpdateResult update() {
        return teams.updateOwned(
                "team-1",
                "owner-1",
                0,
                "Lâm Dạ",
                "",
                NOW
        );
    }

    private static Team team() {
        return new Team(
                "team-1",
                "lam-da",
                "Lâm Dạ",
                "",
                "owner-1",
                Team.State.ACTIVE,
                NOW,
                NOW,
                0
        );
    }

    private static MongoTeamDocument document() {
        return new MongoTeamDocument(
                "team-1",
                "lam-da",
                "Lâm Dạ",
                "",
                "owner-1",
                Team.State.ACTIVE,
                NOW,
                NOW,
                0
        );
    }
}
