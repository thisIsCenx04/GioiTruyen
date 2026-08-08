package com.storyplatform.unit.reading.application;

import com.storyplatform.reading.application.ReadingHistoryService;
import com.storyplatform.reading.application.ReadingProgressException;
import com.storyplatform.reading.application.ReadingProgressOperations;
import com.storyplatform.reading.application.port
        .ReadingHistoryCursorCodec;
import com.storyplatform.reading.application.port.ReadingHistoryRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReadingHistoryServiceTest {

    private static final String USER =
            "10000000-0000-4000-8000-000000000001";
    private static final String OTHER_USER =
            "10000000-0000-4000-8000-000000000002";
    private static final String STORY =
            "20000000-0000-4000-8000-000000000001";
    private static final Instant NOW =
            Instant.parse("2026-07-24T00:00:00Z");
    private final ReadingHistoryRepository repository =
            mock(ReadingHistoryRepository.class);
    private final ReadingHistoryCursorCodec cursors =
            mock(ReadingHistoryCursorCodec.class);

    @Test
    void listsAKeysetPageAndSignsTheLastPrivatePosition() {
        var first = progress(STORY, NOW);
        var second = progress(
                "20000000-0000-4000-8000-000000000002",
                NOW.minusSeconds(1)
        );
        when(repository.list(USER, null, null, 2))
                .thenReturn(List.of(first, second));
        when(cursors.encode(any())).thenReturn("next");

        var page = service().list(USER, null, 1);

        assertThat(page.items()).containsExactly(first);
        assertThat(page.hasMore()).isTrue();
        assertThat(page.nextCursor()).isEqualTo("next");
        verify(cursors).encode(new ReadingHistoryCursorCodec.Cursor(
                USER,
                first.updatedAt(),
                first.storyId()
        ));
    }

    @Test
    void rejectsCrossUserAndMalformedCursors() {
        when(cursors.decode("other")).thenReturn(
                new ReadingHistoryCursorCodec.Cursor(
                        OTHER_USER,
                        NOW,
                        STORY
                )
        );
        assertThatThrownBy(() -> service().list(USER, "other", 20))
                .isInstanceOf(ReadingProgressException.class);
        when(cursors.decode("bad")).thenThrow(
                new IllegalArgumentException("bad")
        );
        assertThatThrownBy(() -> service().list(USER, "bad", 20))
                .isInstanceOf(ReadingProgressException.class);
        assertThatThrownBy(() -> service().list(USER, null, 0))
                .isInstanceOf(ReadingProgressException.class);
        assertThatThrownBy(() -> service().list(USER, null, 101))
                .isInstanceOf(ReadingProgressException.class);
        assertThatThrownBy(() -> service().list("invalid", null, 20))
                .isInstanceOf(ReadingProgressException.class);
        assertThatThrownBy(() -> service().delete(USER, "invalid"))
                .isInstanceOf(ReadingProgressException.class);
    }

    @Test
    void appliesValidCursorAndDeletesOnlyTheCallersStory() {
        var cursor = new ReadingHistoryCursorCodec.Cursor(
                USER,
                NOW,
                STORY
        );
        when(cursors.decode("cursor")).thenReturn(cursor);
        when(repository.list(USER, NOW, STORY, 21))
                .thenReturn(List.of());

        var page = service().list(USER, "cursor", 20);
        service().delete(USER, STORY);

        assertThat(page.items()).isEmpty();
        assertThat(page.hasMore()).isFalse();
        assertThat(page.nextCursor()).isNull();
        verify(repository).delete(USER, STORY);
    }

    @Test
    void treatsABlankCursorAsTheFirstPage() {
        when(repository.list(USER, null, null, 21))
                .thenReturn(List.of());

        assertThat(service().list(USER, " ", 20).items()).isEmpty();
    }

    private ReadingHistoryService service() {
        return new ReadingHistoryService(repository, cursors);
    }

    private static ReadingProgressOperations.ProgressView progress(
            String storyId,
            Instant updatedAt
    ) {
        return new ReadingProgressOperations.ProgressView(
                storyId,
                "30000000-0000-4000-8000-000000000001",
                50,
                updatedAt,
                updatedAt,
                1
        );
    }
}
