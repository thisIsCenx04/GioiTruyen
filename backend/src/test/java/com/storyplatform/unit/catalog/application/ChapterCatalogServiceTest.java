package com.storyplatform.unit.catalog.application;

import com.storyplatform.catalog.application.ChapterCatalogService;
import com.storyplatform.catalog.application.PublicChapterProjection;
import com.storyplatform.catalog.application.PublicStoryProjection;
import com.storyplatform.catalog.application.port.ChapterCursorCodec;
import com.storyplatform.catalog.application.port.ChapterRepository;
import com.storyplatform.catalog.application.port.StoryRepository;
import com.storyplatform.catalog.domain.Story;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChapterCatalogServiceTest {

    @Test
    void returnsAKeysetPageForAPublishedStory() {
        StoryRepository stories = mock(StoryRepository.class);
        ChapterRepository chapters = mock(ChapterRepository.class);
        ChapterCursorCodec cursors = mock(ChapterCursorCodec.class);
        when(stories.findPublishedByIdOrSlug("story"))
                .thenReturn(Optional.of(story()));
        when(chapters.findPublished(any())).thenReturn(List.of(
                chapter(1),
                chapter(2)
        ));
        when(cursors.encode(any())).thenReturn("next");

        var page = new ChapterCatalogService(
                stories,
                chapters,
                cursors
        ).list("story", null, 1);

        assertThat(page.items()).extracting(PublicChapterProjection::number)
                .containsExactly(1);
        assertThat(page.hasMore()).isTrue();
        assertThat(page.nextCursor()).isEqualTo("next");
        verify(chapters).findPublished(new ChapterRepository.ChapterListQuery(
                story().id(),
                null,
                null,
                2
        ));
    }

    @Test
    void rejectsACursorIssuedForAnotherStory() {
        StoryRepository stories = mock(StoryRepository.class);
        ChapterRepository chapters = mock(ChapterRepository.class);
        ChapterCursorCodec cursors = mock(ChapterCursorCodec.class);
        when(stories.findPublishedByIdOrSlug("story"))
                .thenReturn(Optional.of(story()));
        when(cursors.decode("cursor")).thenReturn(
                new ChapterCursorCodec.Cursor(
                        "10000000-0000-4000-8000-000000000099",
                        1,
                        chapter(1).id()
                )
        );

        assertThatThrownBy(() -> new ChapterCatalogService(
                stories,
                chapters,
                cursors
        ).list("story", "cursor", 20))
                .hasMessageContaining("does not belong");
    }

    @Test
    void rejectsInvalidInputsAndUnavailableStories() {
        StoryRepository stories = mock(StoryRepository.class);
        ChapterRepository chapters = mock(ChapterRepository.class);
        ChapterCursorCodec cursors = mock(ChapterCursorCodec.class);
        var service = new ChapterCatalogService(
                stories,
                chapters,
                cursors
        );

        assertThatThrownBy(() -> service.list("", null, 20))
                .hasMessageContaining("identifier");
        assertThatThrownBy(() -> service.list("story", null, 0))
                .hasMessageContaining("limit");
        when(stories.findPublishedByIdOrSlug("story"))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.list("story", null, 20))
                .hasMessageContaining("not found");
    }

    @Test
    void rejectsMalformedCursors() {
        StoryRepository stories = mock(StoryRepository.class);
        ChapterRepository chapters = mock(ChapterRepository.class);
        ChapterCursorCodec cursors = mock(ChapterCursorCodec.class);
        when(stories.findPublishedByIdOrSlug("story"))
                .thenReturn(Optional.of(story()));
        when(cursors.decode("bad"))
                .thenThrow(new IllegalArgumentException("bad"));

        assertThatThrownBy(() -> new ChapterCatalogService(
                stories,
                chapters,
                cursors
        ).list("story", "bad", 20))
                .hasMessageContaining("cursor is invalid");
    }

    @Test
    void appliesAValidCursorAndReturnsTheFinalPage() {
        StoryRepository stories = mock(StoryRepository.class);
        ChapterRepository chapters = mock(ChapterRepository.class);
        ChapterCursorCodec cursors = mock(ChapterCursorCodec.class);
        when(stories.findPublishedByIdOrSlug("story"))
                .thenReturn(Optional.of(story()));
        when(cursors.decode("cursor")).thenReturn(
                new ChapterCursorCodec.Cursor(
                        story().id(),
                        4,
                        chapter(4).id()
                )
        );
        when(chapters.findPublished(any())).thenReturn(List.of());

        var page = new ChapterCatalogService(
                stories,
                chapters,
                cursors
        ).list("story", "cursor", 20);

        assertThat(page.items()).isEmpty();
        assertThat(page.hasMore()).isFalse();
        assertThat(page.nextCursor()).isNull();
        verify(chapters).findPublished(new ChapterRepository.ChapterListQuery(
                story().id(),
                4,
                chapter(4).id(),
                21
        ));
    }

    private static PublicStoryProjection story() {
        Instant now = Instant.parse("2026-07-24T00:00:00Z");
        return new PublicStoryProjection(
                "10000000-0000-4000-8000-000000000001",
                "10000000-0000-4000-8000-000000000002",
                "story",
                "Story",
                "Synopsis",
                List.of(),
                Story.Origin.ORIGINAL,
                "vi",
                Story.CompletionStatus.ONGOING,
                now,
                now,
                1
        );
    }

    private static PublicChapterProjection chapter(int number) {
        return new PublicChapterProjection(
                "20000000-0000-4000-8000-%012d".formatted(number),
                story().id(),
                number,
                "chapter-" + number,
                "Chapter " + number,
                Instant.parse("2026-07-24T00:00:00Z"),
                1
        );
    }
}
