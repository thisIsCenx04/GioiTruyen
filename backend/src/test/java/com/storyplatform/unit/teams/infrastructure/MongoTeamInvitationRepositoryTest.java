package com.storyplatform.unit.teams.infrastructure;

import com.mongodb.client.result.UpdateResult;
import com.storyplatform.teams.domain.TeamInvitation;
import com.storyplatform.teams.infrastructure.persistence
        .MongoTeamInvitationDocument;
import com.storyplatform.teams.infrastructure.persistence
        .MongoTeamInvitationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MongoTeamInvitationRepositoryTest {

    private static final Instant NOW = Instant.parse(
            "2026-07-24T00:00:00Z"
    );

    private MongoTemplate mongo;
    private MongoTeamInvitationRepository repository;

    @BeforeEach
    void setUp() {
        mongo = mock(MongoTemplate.class);
        repository = new MongoTeamInvitationRepository(mongo);
    }

    @Test
    void insertsAndFindsByBothSafeKeys() {
        when(mongo.findOne(
                any(Query.class),
                eq(MongoTeamInvitationDocument.class)
        )).thenReturn(document());

        repository.insert(invitation());
        assertThat(repository.findByIdempotencyKey(
                "team-1",
                "request-123"
        )).isPresent();
        assertThat(repository.findByTokenHash("token-hash")).isPresent();
        verify(mongo).insert(any(MongoTeamInvitationDocument.class));
    }

    @Test
    void acceptUsesPendingExpiryAndVersionPredicate() {
        when(mongo.updateFirst(
                any(Query.class),
                any(Update.class),
                eq(MongoTeamInvitationDocument.class)
        )).thenReturn(
                UpdateResult.acknowledged(1, 1L, null),
                UpdateResult.acknowledged(0, 0L, null)
        );

        assertThat(repository.accept("invitation-1", 0, NOW)).isTrue();
        assertThat(repository.accept("invitation-1", 0, NOW)).isFalse();
    }

    private static TeamInvitation invitation() {
        return new TeamInvitation(
                "invitation-1",
                "team-1",
                "user-2",
                "owner-1",
                Set.of("story:create"),
                "token-hash",
                "request-123",
                TeamInvitation.State.PENDING,
                NOW.plusSeconds(60),
                NOW,
                null,
                0
        );
    }

    private static MongoTeamInvitationDocument document() {
        TeamInvitation value = invitation();
        return new MongoTeamInvitationDocument(
                value.id(),
                value.teamId(),
                value.targetUserId(),
                value.invitedBy(),
                value.permissions(),
                value.tokenHash(),
                value.idempotencyKey(),
                value.state(),
                value.expiresAt(),
                value.createdAt(),
                value.acceptedAt(),
                value.version()
        );
    }
}
