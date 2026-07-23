package com.storyplatform.unit.teams.infrastructure;

import com.mongodb.client.result.UpdateResult;
import com.storyplatform.teams.application.port.TeamMembershipRepository;
import com.storyplatform.teams.domain.TeamMembership;
import com.storyplatform.teams.infrastructure.persistence
        .MongoTeamMembershipDocument;
import com.storyplatform.teams.infrastructure.persistence
        .MongoTeamMembershipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MongoTeamMembershipRepositoryTest {

    private static final Instant NOW = Instant.parse(
            "2026-07-24T00:00:00Z"
    );

    private MongoTemplate mongo;
    private MongoTeamMembershipRepository repository;

    @BeforeEach
    void setUp() {
        mongo = mock(MongoTemplate.class);
        repository = new MongoTeamMembershipRepository(mongo);
    }

    @Test
    void insertsFindsAndListsMemberships() {
        MongoTeamMembershipDocument document = memberDocument();
        when(mongo.findOne(
                any(Query.class),
                eq(MongoTeamMembershipDocument.class)
        )).thenReturn(document);
        when(mongo.find(
                any(Query.class),
                eq(MongoTeamMembershipDocument.class)
        )).thenReturn(List.of(document));

        repository.insert(member());
        assertThat(repository.find("team-1", "user-2")).get()
                .extracting(TeamMembership::state)
                .isEqualTo(TeamMembership.State.INVITED);
        assertThat(repository.list("team-1")).hasSize(1);
        verify(mongo).insert(any(MongoTeamMembershipDocument.class));
    }

    @Test
    void activationUsesStateAndVersionPredicate() {
        when(mongo.updateFirst(
                any(Query.class),
                any(Update.class),
                eq(MongoTeamMembershipDocument.class)
        )).thenReturn(
                UpdateResult.acknowledged(1, 1L, null),
                UpdateResult.acknowledged(0, 0L, null)
        );

        assertThat(repository.activate("team-1", "user-2", 0, NOW))
                .isTrue();
        assertThat(repository.activate("team-1", "user-2", 0, NOW))
                .isFalse();
    }

    @Test
    void removalProtectsOwnerAndHandlesMemberConcurrency() {
        when(mongo.findOne(
                any(Query.class),
                eq(MongoTeamMembershipDocument.class)
        )).thenReturn(
                ownerDocument(),
                memberDocument(),
                memberDocument(),
                null
        );
        when(mongo.updateFirst(
                any(Query.class),
                any(Update.class),
                eq(MongoTeamMembershipDocument.class)
        )).thenReturn(
                UpdateResult.acknowledged(1, 1L, null),
                UpdateResult.acknowledged(0, 0L, null)
        );

        assertThat(repository.revokeMember("team-1", "owner-1", 0))
                .isEqualTo(
                        TeamMembershipRepository.RemovalResult.LAST_OWNER
                );
        assertThat(repository.revokeMember("team-1", "user-2", 0))
                .isEqualTo(TeamMembershipRepository.RemovalResult.REMOVED);
        assertThat(repository.revokeMember("team-1", "user-2", 0))
                .isEqualTo(
                        TeamMembershipRepository.RemovalResult
                                .NOT_FOUND_OR_CONFLICT
                );
        assertThat(repository.revokeMember("team-1", "missing", 0))
                .isEqualTo(
                        TeamMembershipRepository.RemovalResult
                                .NOT_FOUND_OR_CONFLICT
                );
    }

    private static TeamMembership member() {
        return TeamMembership.invited(
                "team-1",
                "user-2",
                Set.of("story:create"),
                NOW
        );
    }

    private static MongoTeamMembershipDocument memberDocument() {
        return new MongoTeamMembershipDocument(
                "team-1:user-2",
                "team-1",
                "user-2",
                TeamMembership.Role.MEMBER,
                Set.of("story:create"),
                TeamMembership.State.INVITED,
                NOW,
                0
        );
    }

    private static MongoTeamMembershipDocument ownerDocument() {
        return new MongoTeamMembershipDocument(
                "team-1:owner-1",
                "team-1",
                "owner-1",
                TeamMembership.Role.OWNER,
                Set.of("team:manage"),
                TeamMembership.State.ACTIVE,
                NOW,
                0
        );
    }
}
