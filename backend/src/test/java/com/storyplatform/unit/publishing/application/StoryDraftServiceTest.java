package com.storyplatform.unit.publishing.application;

import com.storyplatform.catalog.application.contract.ActiveCategoryDirectory;
import com.storyplatform.publishing.application.StoryDraftException;
import com.storyplatform.publishing.application.StoryDraftOperations;
import com.storyplatform.publishing.application.StoryDraftService;
import com.storyplatform.publishing.application.port.StoryDraftRepository;
import com.storyplatform.publishing.domain.StoryRevision;
import com.storyplatform.publishing.domain.StoryDraft;
import com.storyplatform.shared.events.IntegrationEvent;
import com.storyplatform.shared.events.persistence.OutboxAppender;
import com.storyplatform.teams.application.contract.TeamPermissionAuthorizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StoryDraftServiceTest {

    private static final Instant NOW =
            Instant.parse("2026-07-24T00:00:00Z");
    private static final String ACTOR =
            "10000000-0000-4000-8000-000000000001";
    private static final String TEAM =
            "20000000-0000-4000-8000-000000000001";
    private static final String OTHER_TEAM =
            "20000000-0000-4000-8000-000000000002";
    private static final String CATEGORY =
            "30000000-0000-4000-8000-000000000001";
    private static final String STORY =
            "40000000-0000-4000-8000-000000000001";
    private static final String REVISION =
            "50000000-0000-4000-8000-000000000001";
    private static final String EVENT =
            "60000000-0000-4000-8000-000000000001";
    private static final String KEY = "draft-request-001";

    private final TeamPermissionAuthorizer permissions =
            mock(TeamPermissionAuthorizer.class);
    private final StoryDraftRepository drafts =
            mock(StoryDraftRepository.class);
    private final OutboxAppender outbox = mock(OutboxAppender.class);
    private final ArrayDeque<String> ids = new ArrayDeque<>();

    @BeforeEach
    void setUp() {
        ids.addAll(List.of(STORY, REVISION, EVENT));
        when(permissions.allows(
                ACTOR,
                TEAM,
                StoryDraftService.CREATE_PERMISSION
        )).thenReturn(true);
    }

    @Test
    void createsTeamOwnedStoryRevisionAndMinimalOutboxEvent() {
        StoryDraftService service = service();
        ArgumentCaptor<StoryDraft> story =
                ArgumentCaptor.forClass(StoryDraft.class);
        ArgumentCaptor<StoryRevision> revision =
                ArgumentCaptor.forClass(StoryRevision.class);
        ArgumentCaptor<IntegrationEvent> event =
                ArgumentCaptor.forClass(IntegrationEvent.class);

        StoryDraftOperations.DraftView result = service.create(
                ACTOR,
                TEAM,
                KEY,
                command("  Người Giữ Đèn  ")
        );

        verify(drafts).insert(
                story.capture(),
                revision.capture(),
                org.mockito.ArgumentMatchers.eq(ACTOR),
                org.mockito.ArgumentMatchers.eq(KEY),
                any()
        );
        verify(outbox).append(event.capture());
        assertThat(result.id()).isEqualTo(STORY);
        assertThat(result.teamId()).isEqualTo(TEAM);
        assertThat(result.workflowStatus())
                .isEqualTo(StoryDraft.WorkflowStatus.DRAFT);
        assertThat(result.version()).isEqualTo(1);
        assertThat(result.revisionNo()).isEqualTo(1);
        assertThat(result.slug()).startsWith("nguoi-giu-den-");
        assertThat(story.getValue().currentRevision()).isEqualTo(REVISION);
        assertThat(revision.getValue().checksum())
                .matches("[0-9a-f]{64}");
        assertThat(revision.getValue().snapshot().title())
                .isEqualTo("Người Giữ Đèn");
        assertThat(event.getValue().eventType())
                .isEqualTo(StoryDraftService.EVENT_TYPE);
        assertThat(event.getValue().aggregateId()).isEqualTo(STORY);
        assertThat(event.getValue().payload())
                .isInstanceOf(StoryDraftService.StoryDraftCreated.class);
    }

    @Test
    void sameTeamAndKeyReturnsPriorDraftWithoutSideEffects() {
        StoryDraftService first = service();
        StoryDraftOperations.DraftView created = first.create(
                ACTOR,
                TEAM,
                KEY,
                command("Truyện Một")
        );
        ArgumentCaptor<String> fingerprint =
                ArgumentCaptor.forClass(String.class);
        verify(drafts).insert(
                any(),
                any(),
                any(),
                any(),
                fingerprint.capture()
        );
        StoryDraft stored = story(created);
        when(drafts.findReplay(TEAM, KEY)).thenReturn(Optional.of(
                new StoryDraftRepository.StoredDraft(
                        stored,
                        1,
                        fingerprint.getValue()
                )
        ));

        StoryDraftOperations.DraftView replayed = service().create(
                ACTOR,
                TEAM,
                KEY,
                command("Truyện Một")
        );

        assertThat(replayed).isEqualTo(created);
        verify(drafts, times(1)).insert(
                any(), any(), any(), any(), any()
        );
        verify(outbox, times(1)).append(any());
    }

    @Test
    void rejectsSameKeyWithDifferentBody() {
        when(drafts.findReplay(TEAM, KEY)).thenReturn(Optional.of(
                new StoryDraftRepository.StoredDraft(
                        story(view()),
                        1,
                        "0".repeat(64)
                )
        ));

        assertThatThrownBy(() -> service().create(
                ACTOR,
                TEAM,
                KEY,
                command("Nội dung khác")
        ))
                .isInstanceOf(StoryDraftException.class)
                .extracting("code")
                .isEqualTo("IDEMPOTENCY_KEY_REUSED");
        verify(outbox, never()).append(any());
    }

    @Test
    void deniesCrossTeamCreationBeforeRepositoryAccess() {
        assertThatThrownBy(() -> service().create(
                ACTOR,
                OTHER_TEAM,
                KEY,
                command("Truyện Một")
        ))
                .isInstanceOf(StoryDraftException.class)
                .extracting("code")
                .isEqualTo("STORY_CREATE_FORBIDDEN");

        verify(drafts, never()).findReplay(any(), any());
        verify(drafts, never()).insert(
                any(), any(), any(), any(), any()
        );
    }

    @Test
    void rejectsUnknownOrDuplicateTaxonomy() {
        StoryDraftOperations.CreateCommand unknown =
                new StoryDraftOperations.CreateCommand(
                        "Truyện Một",
                        "Tóm tắt",
                        StoryDraft.Origin.ORIGINAL,
                        "vi",
                        List.of(
                                "30000000-0000-4000-8000-000000000099"
                        ),
                        null
                );
        StoryDraftOperations.CreateCommand duplicate =
                new StoryDraftOperations.CreateCommand(
                        "Truyện Một",
                        "Tóm tắt",
                        StoryDraft.Origin.ORIGINAL,
                        "vi",
                        List.of(CATEGORY, CATEGORY),
                        null
                );

        assertThatThrownBy(() ->
                service().create(ACTOR, TEAM, KEY, unknown))
                .extracting("code")
                .isEqualTo("STORY_DRAFT_INVALID");
        assertThatThrownBy(() ->
                service().create(ACTOR, TEAM, KEY, duplicate))
                .extracting("code")
                .isEqualTo("STORY_DRAFT_INVALID");
    }

    @Test
    void rejectsInvalidKeyBodyLanguageAndCover() {
        assertThatThrownBy(() -> service().create(
                ACTOR,
                TEAM,
                "short",
                command("Truyện Một")
        )).extracting("code").isEqualTo("STORY_DRAFT_INVALID");
        assertThatThrownBy(() -> service().create(
                ACTOR,
                TEAM,
                KEY,
                null
        )).extracting("code").isEqualTo("STORY_DRAFT_INVALID");
        assertThatThrownBy(() -> service().create(
                ACTOR,
                TEAM,
                KEY,
                new StoryDraftOperations.CreateCommand(
                        null,
                        "Tóm tắt",
                        StoryDraft.Origin.ORIGINAL,
                        "vi",
                        List.of(CATEGORY),
                        null
                )
        )).extracting("code").isEqualTo("STORY_DRAFT_INVALID");
        assertThatThrownBy(() -> service().create(
                ACTOR,
                TEAM,
                KEY,
                new StoryDraftOperations.CreateCommand(
                        "Truyện Một",
                        "Tóm tắt",
                        null,
                        "vi",
                        List.of(),
                        null
                )
        )).extracting("code").isEqualTo("STORY_DRAFT_INVALID");
        assertThatThrownBy(() -> service().create(
                ACTOR,
                TEAM,
                KEY,
                new StoryDraftOperations.CreateCommand(
                        "Truyện Một",
                        "Tóm tắt",
                        StoryDraft.Origin.ORIGINAL,
                        "bad_language",
                        List.of(CATEGORY),
                        null
                )
        )).extracting("code").isEqualTo("STORY_DRAFT_INVALID");
        assertThatThrownBy(() -> service().create(
                ACTOR,
                TEAM,
                KEY,
                new StoryDraftOperations.CreateCommand(
                        "Truyện Một",
                        "Tóm tắt",
                        StoryDraft.Origin.ORIGINAL,
                        "vi",
                        List.of(CATEGORY),
                        "bad-cover"
                )
        )).extracting("code").isEqualTo("STORY_DRAFT_INVALID");
    }

    @Test
    void inactiveTeamIsDeniedWithoutPermissionLookup() {
        StoryDraftService service = new StoryDraftService(
                permissions,
                teamId -> false,
                () -> java.util.Set.of(CATEGORY),
                drafts,
                outbox,
                ids::removeFirst,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        assertThatThrownBy(() -> service.create(
                ACTOR,
                TEAM,
                KEY,
                command("Truyện Một")
        )).extracting("code").isEqualTo("STORY_CREATE_FORBIDDEN");

        verify(permissions, never()).allows(any(), any(), any());
    }

    @Test
    void createsFallbackSlugForNonLatinTitle() {
        StoryDraftOperations.DraftView fallback = service().create(
                ACTOR,
                TEAM,
                KEY,
                command("中文")
        );

        assertThat(fallback.slug()).isEqualTo("story-40000000");
    }

    private StoryDraftService service() {
        ActiveCategoryDirectory taxonomy = () ->
                java.util.Set.of(CATEGORY);
        return new StoryDraftService(
                permissions,
                teamId -> TEAM.equals(teamId)
                        || OTHER_TEAM.equals(teamId),
                taxonomy,
                drafts,
                outbox,
                ids::removeFirst,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private static StoryDraftOperations.CreateCommand command(
            String title
    ) {
        return new StoryDraftOperations.CreateCommand(
                title,
                "Tóm tắt",
                StoryDraft.Origin.ORIGINAL,
                "vi",
                List.of(CATEGORY),
                null
        );
    }

    private static StoryDraft story(StoryDraftOperations.DraftView view) {
        return new StoryDraft(
                view.id(),
                view.teamId(),
                view.slug(),
                view.title(),
                view.synopsis(),
                view.categoryIds(),
                view.origin(),
                view.language(),
                view.completionStatus(),
                view.workflowStatus(),
                view.currentRevision(),
                view.coverAssetId(),
                view.createdAt(),
                view.updatedAt(),
                view.version()
        );
    }

    private static StoryDraftOperations.DraftView view() {
        return new StoryDraftOperations.DraftView(
                STORY,
                TEAM,
                "truyen-mot-40000000",
                "Truyện Một",
                "Tóm tắt",
                StoryDraft.Origin.ORIGINAL,
                "vi",
                List.of(CATEGORY),
                null,
                StoryDraft.CompletionStatus.ONGOING,
                StoryDraft.WorkflowStatus.DRAFT,
                REVISION,
                1,
                1,
                NOW,
                NOW
        );
    }
}
