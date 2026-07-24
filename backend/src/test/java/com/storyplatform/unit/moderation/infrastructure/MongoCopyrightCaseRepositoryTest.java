package com.storyplatform.unit.moderation.infrastructure;

import com.mongodb.client.result.UpdateResult;
import com.storyplatform.moderation.application.CopyrightCaseOperations;
import com.storyplatform.moderation.application.port.CopyrightCaseRepository;
import com.storyplatform.moderation.infrastructure.persistence
        .MongoCopyrightCaseDocument;
import com.storyplatform.moderation.infrastructure.persistence
        .MongoCopyrightCaseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MongoCopyrightCaseRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-07-24T00:00:00Z");

    @Test
    void validatesPublicStoryAndPrivateOwnedEvidence() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.exists(any(Query.class), eq("stories")))
                .thenReturn(true);
        when(mongo.count(any(Query.class), eq("media_assets")))
                .thenReturn(2L);
        var repository = new MongoCopyrightCaseRepository(mongo);

        assertThat(repository.storyIsPublic("story")).isTrue();
        assertThat(repository.evidenceIsPrivateAndOwned(
                "actor", List.of("one", "two")
        )).isTrue();
        when(mongo.count(any(Query.class), eq("media_assets")))
                .thenReturn(1L);
        assertThat(repository.evidenceIsPrivateAndOwned(
                "actor", List.of("one", "two")
        )).isFalse();
    }

    @Test
    void createsCaseAndTemporaryHoldAtomically() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.insert(any(MongoCopyrightCaseDocument.class)))
                .thenAnswer(call -> call.getArgument(0));
        when(mongo.updateFirst(
                any(Query.class), any(Update.class), eq("stories")
        )).thenReturn(UpdateResult.acknowledged(1, 1L, null));
        var repository = new MongoCopyrightCaseRepository(mongo);

        assertThat(repository.createAndHold(view()).outcome())
                .isEqualTo(CopyrightCaseRepository.Outcome.SUCCESS);

        when(mongo.updateFirst(
                any(Query.class), any(Update.class), eq("stories")
        )).thenReturn(UpdateResult.acknowledged(0, 0L, null));
        assertThat(repository.createAndHold(view()).outcome())
                .isEqualTo(CopyrightCaseRepository.Outcome.STORY_CHANGED);

        doThrow(new DuplicateKeyException("duplicate"))
                .when(mongo).insert(any(MongoCopyrightCaseDocument.class));
        assertThat(repository.createAndHold(view()).outcome())
                .isEqualTo(CopyrightCaseRepository.Outcome.DUPLICATE);
    }

    @Test
    void allowsActiveTeamMemberToAppealOnce() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        MongoCopyrightCaseDocument document = document("PENDING", null);
        when(mongo.findById(
                "case", MongoCopyrightCaseDocument.class
        )).thenReturn(document);
        when(mongo.findById(
                "story",
                MongoCopyrightCaseRepository.TeamProjection.class,
                "stories"
        )).thenReturn(new MongoCopyrightCaseRepository.TeamProjection(
                "story", "team"
        ));
        when(mongo.exists(any(Query.class), eq("team_memberships")))
                .thenReturn(true);
        when(mongo.findAndModify(
                any(Query.class),
                any(Update.class),
                any(FindAndModifyOptions.class),
                eq(MongoCopyrightCaseDocument.class)
        )).thenReturn(document("APPEALED", null), null);
        var repository = new MongoCopyrightCaseRepository(mongo);

        assertThat(repository.appeal(
                "case", "actor", "Counter", NOW
        ).outcome()).isEqualTo(CopyrightCaseRepository.Outcome.SUCCESS);
        assertThat(repository.appeal(
                "case", "actor", "Counter", NOW
        ).outcome()).isEqualTo(CopyrightCaseRepository.Outcome.DUPLICATE);

        when(mongo.exists(any(Query.class), eq("team_memberships")))
                .thenReturn(false);
        assertThat(repository.appeal(
                "case", "actor", "Counter", NOW
        ).outcome()).isEqualTo(CopyrightCaseRepository.Outcome.STORY_CHANGED);
        when(mongo.findById(
                "case", MongoCopyrightCaseDocument.class
        )).thenReturn(null);
        assertThat(repository.appeal(
                "case", "actor", "Counter", NOW
        ).outcome()).isEqualTo(CopyrightCaseRepository.Outcome.STORY_CHANGED);
    }

    @Test
    void commitsTakedownOrReinstatementAndRejectsRaces() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.findAndModify(
                any(Query.class),
                any(Update.class),
                any(FindAndModifyOptions.class),
                eq(MongoCopyrightCaseDocument.class)
        )).thenReturn(
                document("TAKEDOWN", "TAKEDOWN"),
                document("REINSTATED", "REINSTATE"),
                null
        );
        when(mongo.updateFirst(
                any(Query.class), any(Update.class), eq("stories")
        )).thenReturn(
                UpdateResult.acknowledged(1, 1L, null),
                UpdateResult.acknowledged(1, 1L, null)
        );
        var repository = new MongoCopyrightCaseRepository(mongo);

        assertThat(repository.decide(
                "case",
                "reviewer",
                CopyrightCaseOperations.Decision.TAKEDOWN,
                "INFRINGEMENT_CONFIRMED",
                "Final",
                NOW
        ).copyrightCase().status()).isEqualTo("TAKEDOWN");
        assertThat(repository.decide(
                "case",
                "reviewer",
                CopyrightCaseOperations.Decision.REINSTATE,
                "COUNTER_NOTICE_VALID",
                "Release",
                NOW
        ).copyrightCase().status()).isEqualTo("REINSTATED");
        assertThat(repository.decide(
                "case",
                "reviewer",
                CopyrightCaseOperations.Decision.TAKEDOWN,
                "INFRINGEMENT_CONFIRMED",
                "Final",
                NOW
        ).outcome()).isEqualTo(CopyrightCaseRepository.Outcome.DUPLICATE);
    }

    private static CopyrightCaseOperations.CopyrightCaseView view() {
        return document("PENDING", null).toView();
    }

    private static MongoCopyrightCaseDocument document(
            String status,
            String decision
    ) {
        return new MongoCopyrightCaseDocument(
                "case",
                "story",
                "claimant",
                "Author",
                "Statement",
                List.of("evidence"),
                status,
                NOW,
                NOW.plusSeconds(1),
                NOW.plusSeconds(2),
                null,
                null,
                null,
                decision,
                decision == null ? null : "REASON",
                decision == null ? null : "Note",
                decision == null ? null : "reviewer",
                decision == null ? null : NOW
        );
    }
}
