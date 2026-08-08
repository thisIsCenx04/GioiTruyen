package com.storyplatform.unit.reading.application;

import com.storyplatform.reading.application.ReadingProgressException;
import com.storyplatform.reading.application.ReadingProgressOperations;
import com.storyplatform.reading.application.ReadingProgressService;
import com.storyplatform.reading.application.port.ReadingProgressRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReadingProgressServiceTest {

    private static final String USER =
            "10000000-0000-4000-8000-000000000001";
    private static final String STORY =
            "20000000-0000-4000-8000-000000000001";
    private static final String CHAPTER =
            "30000000-0000-4000-8000-000000000001";
    private static final Instant NOW =
            Instant.parse("2026-07-24T00:00:00Z");
    private final ReadingProgressRepository repository =
            mock(ReadingProgressRepository.class);

    @Test
    void createsProgressForAPublishedChapter() {
        when(repository.chapterIsPublished(STORY, CHAPTER))
                .thenReturn(true);
        when(repository.create(any(), any())).thenReturn(true);

        var progress = service().synchronize(
                USER,
                STORY,
                null,
                command(NOW.minusSeconds(2), 25)
        );

        assertThat(progress.version()).isEqualTo(1);
        assertThat(progress.position()).isEqualTo(25);
        verify(repository).create(USER, progress);
    }

    @Test
    void updatesANewerDeviceValueWithOptimisticVersion() {
        var current = progress(NOW.minusSeconds(10), 20, 4);
        when(repository.chapterIsPublished(STORY, CHAPTER))
                .thenReturn(true);
        when(repository.find(USER, STORY)).thenReturn(Optional.of(
                new ReadingProgressRepository.StoredProgress(USER, current)
        ));
        when(repository.update(
                any(), any(), any(Long.class), any()
        )).thenReturn(true);

        var updated = service().synchronize(
                USER,
                STORY,
                4L,
                command(NOW.minusSeconds(1), 55)
        );

        assertThat(updated.version()).isEqualTo(5);
        assertThat(updated.position()).isEqualTo(55);
    }

    @Test
    void olderDeviceUpdateKeepsTheServerWinnerWithoutWriting() {
        var current = progress(NOW.minusSeconds(1), 80, 5);
        when(repository.chapterIsPublished(STORY, CHAPTER))
                .thenReturn(true);
        when(repository.find(USER, STORY)).thenReturn(Optional.of(
                new ReadingProgressRepository.StoredProgress(USER, current)
        ));

        var result = service().synchronize(
                USER,
                STORY,
                4L,
                command(NOW.minusSeconds(30), 10)
        );

        assertThat(result).isEqualTo(current);
        verify(repository, never()).update(any(), any(), any(Long.class), any());
    }

    @Test
    void rejectsVersionRacesInvalidClocksAndUnpublishedChapters() {
        when(repository.chapterIsPublished(STORY, CHAPTER))
                .thenReturn(true);
        when(repository.find(USER, STORY)).thenReturn(Optional.of(
                new ReadingProgressRepository.StoredProgress(
                        USER,
                        progress(NOW.minusSeconds(10), 20, 4)
                )
        ));
        assertThatThrownBy(() -> service().synchronize(
                USER, STORY, 3L, command(NOW, 30)
        )).isInstanceOf(ReadingProgressException.class)
                .extracting("code")
                .isEqualTo("READING_PROGRESS_CONFLICT");
        assertThatThrownBy(() -> service().synchronize(
                USER,
                STORY,
                4L,
                command(NOW.plusSeconds(301), 30)
        )).isInstanceOf(ReadingProgressException.class);

        when(repository.chapterIsPublished(STORY, CHAPTER))
                .thenReturn(false);
        assertThatThrownBy(() -> service().synchronize(
                USER, STORY, null, command(NOW, 30)
        )).isInstanceOf(ReadingProgressException.class)
                .extracting("code")
                .isEqualTo("READING_CHAPTER_NOT_FOUND");
    }

    @Test
    void getsOnlyTheAuthenticatedUsersProgress() {
        var current = progress(NOW, 30, 2);
        when(repository.find(USER, STORY)).thenReturn(Optional.of(
                new ReadingProgressRepository.StoredProgress(USER, current)
        ));

        assertThat(service().get(USER, STORY)).isEqualTo(current);
        assertThatThrownBy(() -> service().get(
                USER,
                "20000000-0000-4000-8000-000000000002"
        )).isInstanceOf(ReadingProgressException.class)
                .extracting("code")
                .isEqualTo("READING_PROGRESS_NOT_FOUND");
    }

    @Test
    void validatesEveryUntrustedProgressBoundary() {
        assertThatThrownBy(() -> service().synchronize(
                USER, STORY, null, null
        )).isInstanceOf(ReadingProgressException.class);
        assertThatThrownBy(() -> service().synchronize(
                USER, STORY, null, command(NOW, Double.NaN)
        )).isInstanceOf(ReadingProgressException.class);
        assertThatThrownBy(() -> service().synchronize(
                USER, STORY, null, command(NOW, -1)
        )).isInstanceOf(ReadingProgressException.class);
        assertThatThrownBy(() -> service().synchronize(
                USER, STORY, null, command(NOW, 101)
        )).isInstanceOf(ReadingProgressException.class);
        assertThatThrownBy(() -> service().synchronize(
                USER,
                STORY,
                null,
                new ReadingProgressOperations.SyncCommand(
                        CHAPTER, 1, null
                )
        )).isInstanceOf(ReadingProgressException.class);
        assertThatThrownBy(() -> service().synchronize(
                "invalid", STORY, null, command(NOW, 1)
        )).isInstanceOf(ReadingProgressException.class);
    }

    @Test
    void reportsCreateAndCompareAndSetRaces() {
        when(repository.chapterIsPublished(STORY, CHAPTER))
                .thenReturn(true);
        assertThatThrownBy(() -> service().synchronize(
                USER, STORY, 1L, command(NOW, 10)
        )).isInstanceOf(ReadingProgressException.class);

        when(repository.create(any(), any())).thenReturn(false);
        assertThatThrownBy(() -> service().synchronize(
                USER, STORY, null, command(NOW, 10)
        )).isInstanceOf(ReadingProgressException.class);

        var current = progress(NOW.minusSeconds(10), 20, 4);
        when(repository.find(USER, STORY)).thenReturn(Optional.of(
                new ReadingProgressRepository.StoredProgress(USER, current)
        ));
        when(repository.update(
                any(), any(), any(Long.class), any()
        )).thenReturn(false);
        assertThatThrownBy(() -> service().synchronize(
                USER, STORY, 4L, command(NOW, 40)
        )).isInstanceOf(ReadingProgressException.class);
    }

    private ReadingProgressService service() {
        return new ReadingProgressService(
                repository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private static ReadingProgressOperations.SyncCommand command(
            Instant at,
            double position
    ) {
        return new ReadingProgressOperations.SyncCommand(
                CHAPTER,
                position,
                at
        );
    }

    private static ReadingProgressOperations.ProgressView progress(
            Instant at,
            double position,
            long version
    ) {
        return new ReadingProgressOperations.ProgressView(
                STORY,
                CHAPTER,
                position,
                at,
                NOW,
                version
        );
    }
}
