package com.storyplatform.unit.publishing.application;

import com.storyplatform.publishing.application.ChapterContentSanitizer;
import com.storyplatform.publishing.application.ChapterDraftException;
import com.storyplatform.publishing.application.ChapterDraftService;
import com.storyplatform.publishing.application.port.ChapterDraftRepository;
import com.storyplatform.publishing.application.port.StoryDraftRepository;
import com.storyplatform.publishing.domain.ChapterDraft;
import com.storyplatform.publishing.domain.ChapterRevision;
import com.storyplatform.publishing.domain.StoryDraft;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChapterDraftServiceTest {

    private static final String ACTOR =
            "10000000-0000-4000-8000-000000000001";
    private static final String TEAM =
            "20000000-0000-4000-8000-000000000001";
    private static final String STORY =
            "40000000-0000-4000-8000-000000000001";
    private static final String CHAPTER =
            "60000000-0000-4000-8000-000000000001";
    private static final String REVISION =
            "70000000-0000-4000-8000-000000000001";
    private static final Instant NOW =
            Instant.parse("2026-07-24T00:00:00Z");

    private final TeamPermissionAuthorizer permissions =
            mock(TeamPermissionAuthorizer.class);
    private final StoryDraftRepository stories =
            mock(StoryDraftRepository.class);
    private final ChapterDraftRepository chapters =
            mock(ChapterDraftRepository.class);

    @BeforeEach
    void setUp() {
        when(permissions.allows(
                ACTOR,
                TEAM,
                ChapterDraftService.EDIT_PERMISSION
        )).thenReturn(true);
        when(stories.findOwned(TEAM, STORY)).thenReturn(Optional.of(
                new StoryDraftRepository.StoredDraft(story(), 1, "a".repeat(64))
        ));
    }

    @Test
    void createsSanitizedImmutableFirstRevision() {
        ArgumentCaptor<ChapterDraft> chapter =
                ArgumentCaptor.forClass(ChapterDraft.class);
        ArgumentCaptor<ChapterRevision> revision =
                ArgumentCaptor.forClass(ChapterRevision.class);

        var result = service().create(
                ACTOR,
                TEAM,
                STORY,
                new com.storyplatform.publishing.application
                        .ChapterDraftOperations.CreateCommand(
                        1,
                        " Mở đầu ",
                        "<p onclick=\"bad()\">Hello <b>world</b></p>"
                )
        );

        verify(chapters).insert(chapter.capture(), revision.capture());
        assertThat(result.id()).isEqualTo(CHAPTER);
        assertThat(result.revisionNo()).isEqualTo(1);
        assertThat(result.wordCount()).isEqualTo(2);
        assertThat(result.slug()).startsWith("chapter-1-mo-dau-");
        assertThat(revision.getValue().contentHtml())
                .doesNotContain("onclick", "<b>");
        assertThat(revision.getValue().checksum())
                .matches("[0-9a-f]{64}");
        assertThat(chapter.getValue().currentRevision())
                .isEqualTo(REVISION);
    }

    @Test
    void deniesUnauthorizedTeamBeforeLookingUpStory() {
        assertThatThrownBy(() -> service().create(
                ACTOR,
                "20000000-0000-4000-8000-000000000002",
                STORY,
                command()
        )).isInstanceOf(ChapterDraftException.class)
                .extracting("code")
                .isEqualTo("CHAPTER_CREATE_FORBIDDEN");

        verify(stories, never()).findOwned(any(), any());
    }

    @Test
    void rejectsMissingOwnedStoryAndDuplicateNumber() {
        when(stories.findOwned(TEAM, STORY)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service().create(
                ACTOR, TEAM, STORY, command()
        )).isInstanceOf(ChapterDraftException.class)
                .extracting("code")
                .isEqualTo("STORY_DRAFT_NOT_FOUND");

        when(stories.findOwned(TEAM, STORY)).thenReturn(Optional.of(
                new StoryDraftRepository.StoredDraft(story(), 1, "a".repeat(64))
        ));
        when(chapters.numberExists(STORY, 1)).thenReturn(true);
        assertThatThrownBy(() -> service().create(
                ACTOR, TEAM, STORY, command()
        )).isInstanceOf(ChapterDraftException.class)
                .extracting("code")
                .isEqualTo("CHAPTER_NUMBER_EXISTS");
    }

    @Test
    void rejectsInvalidChapterMetadataBeforePersistence() {
        assertThatThrownBy(() -> service().create(
                ACTOR, TEAM, STORY, null
        )).isInstanceOf(ChapterDraftException.class)
                .extracting("code")
                .isEqualTo("CHAPTER_DRAFT_INVALID");
        assertThatThrownBy(() -> service().create(
                ACTOR,
                TEAM,
                STORY,
                new com.storyplatform.publishing.application
                        .ChapterDraftOperations.CreateCommand(
                        0, "Title", "<p>content</p>"
                )
        )).isInstanceOf(ChapterDraftException.class);
        assertThatThrownBy(() -> service().create(
                ACTOR,
                TEAM,
                STORY,
                new com.storyplatform.publishing.application
                        .ChapterDraftOperations.CreateCommand(
                        1, " ", "<p>content</p>"
                )
        )).isInstanceOf(ChapterDraftException.class);
        assertThatThrownBy(() -> service().create(
                ACTOR,
                TEAM,
                STORY,
                new com.storyplatform.publishing.application
                        .ChapterDraftOperations.CreateCommand(
                        1, "x".repeat(201), "<p>content</p>"
                )
        )).isInstanceOf(ChapterDraftException.class);
        verify(chapters, never()).insert(any(), any());
    }

    @Test
    void updatesWithCompareAndSetAndCreatesTheNextImmutableRevision() {
        when(chapters.findOwned(TEAM, STORY, CHAPTER))
                .thenReturn(Optional.of(
                        new ChapterDraftRepository.StoredChapter(chapter())
                ));
        when(chapters.update(any(), any(), org.mockito.ArgumentMatchers.eq(1L)))
                .thenReturn(true);
        ArgumentCaptor<ChapterDraft> chapter =
                ArgumentCaptor.forClass(ChapterDraft.class);
        ArgumentCaptor<ChapterRevision> revision =
                ArgumentCaptor.forClass(ChapterRevision.class);

        var result = updateService().update(
                ACTOR,
                TEAM,
                STORY,
                CHAPTER,
                1,
                new com.storyplatform.publishing.application
                        .ChapterDraftOperations.UpdateCommand(
                        " ChÆ°Æ¡ng má»›i ",
                        "<p onclick=\"bad()\">Hello <b>again</b></p>"
                )
        );

        verify(chapters).update(
                chapter.capture(),
                revision.capture(),
                org.mockito.ArgumentMatchers.eq(1L)
        );
        assertThat(result.version()).isEqualTo(2);
        assertThat(result.revisionNo()).isEqualTo(2);
        assertThat(chapter.getValue().currentRevision()).isEqualTo(REVISION);
        assertThat(revision.getValue().contentHtml())
                .doesNotContain("onclick", "<b>");
    }

    @Test
    void rejectsAStaleChapterWithoutWritingARevision() {
        when(chapters.findOwned(TEAM, STORY, CHAPTER))
                .thenReturn(Optional.of(
                        new ChapterDraftRepository.StoredChapter(chapter())
                ));

        assertThatThrownBy(() -> updateService().update(
                ACTOR,
                TEAM,
                STORY,
                CHAPTER,
                2,
                new com.storyplatform.publishing.application
                        .ChapterDraftOperations.UpdateCommand(
                        "Title",
                        "<p>content</p>"
                )
        )).isInstanceOf(ChapterDraftException.class)
                .extracting("code")
                .isEqualTo("CHAPTER_VERSION_STALE");

        verify(chapters, never()).update(
                any(),
                any(),
                org.mockito.ArgumentMatchers.anyLong()
        );
    }

    private ChapterDraftService service() {
        ArrayDeque<String> ids = new ArrayDeque<>(
                List.of(CHAPTER, REVISION)
        );
        return service(ids);
    }

    private ChapterDraftService updateService() {
        return service(new ArrayDeque<>(List.of(REVISION)));
    }

    private ChapterDraftService service(ArrayDeque<String> ids) {
        return new ChapterDraftService(
                permissions,
                TEAM::equals,
                stories,
                chapters,
                new ChapterContentSanitizer(),
                ids::removeFirst,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private static com.storyplatform.publishing.application
            .ChapterDraftOperations.CreateCommand command() {
        return new com.storyplatform.publishing.application
                .ChapterDraftOperations.CreateCommand(
                1, "Mở đầu", "<p>Hello world</p>"
        );
    }

    private static StoryDraft story() {
        return new StoryDraft(
                STORY,
                TEAM,
                "story",
                "Story",
                "Synopsis",
                List.of("30000000-0000-4000-8000-000000000001"),
                StoryDraft.Origin.ORIGINAL,
                "vi",
                StoryDraft.CompletionStatus.ONGOING,
                StoryDraft.WorkflowStatus.DRAFT,
                "50000000-0000-4000-8000-000000000001",
                null,
                NOW,
                NOW,
                1
        );
    }

    private static ChapterDraft chapter() {
        return new ChapterDraft(
                CHAPTER,
                STORY,
                TEAM,
                1,
                "chapter-1",
                "Chapter",
                ChapterDraft.WorkflowStatus.DRAFT,
                "70000000-0000-4000-8000-000000000000",
                1,
                NOW,
                NOW,
                1
        );
    }
}
