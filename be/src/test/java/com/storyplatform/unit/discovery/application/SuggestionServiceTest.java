package com.storyplatform.unit.discovery.application;

import com.storyplatform.discovery.application.SuggestionOperations;
import com.storyplatform.discovery.application.SuggestionRateLimitException;
import com.storyplatform.discovery.application.SuggestionService;
import com.storyplatform.discovery.application.port.SearchCursorCodec;
import com.storyplatform.discovery.application.port.SuggestionRateLimiter;
import com.storyplatform.discovery.application.port.SuggestionRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SuggestionServiceTest {

    @Test
    void normalizesInputAppliesSearchAfterAndWrapsNextCursor() {
        SuggestionRepository repository =
                mock(SuggestionRepository.class);
        SearchCursorCodec cursors = mock(SearchCursorCodec.class);
        SuggestionRateLimiter limiter =
                mock(SuggestionRateLimiter.class);
        when(limiter.allow("client")).thenReturn(true);
        when(cursors.decode("signed")).thenReturn("atlas");
        when(cursors.encode("next-atlas")).thenReturn("next-signed");
        when(repository.find("Thế giới", "atlas", 8)).thenReturn(
                new SuggestionRepository.SuggestionPage(
                        List.of(),
                        "next-atlas",
                        true
                )
        );

        var response = service(repository, cursors, limiter).suggest(
                new SuggestionOperations.SuggestionRequest(
                        "  Thế   giới ",
                        "signed",
                        8,
                        "client"
                )
        );

        assertThat(response.nextCursor()).isEqualTo("next-signed");
        assertThat(response.hasMore()).isTrue();
        verify(repository).find("Thế giới", "atlas", 8);
    }

    @Test
    void rateLimitRunsBeforeQueryValidation() {
        SuggestionRepository repository =
                mock(SuggestionRepository.class);
        SearchCursorCodec cursors = mock(SearchCursorCodec.class);
        SuggestionRateLimiter limiter =
                mock(SuggestionRateLimiter.class);
        when(limiter.allow("client")).thenReturn(false);
        when(limiter.retryAfterSeconds()).thenReturn(60L);

        assertThatThrownBy(() -> service(
                repository,
                cursors,
                limiter
        ).suggest(new SuggestionOperations.SuggestionRequest(
                "x", null, 8, "client"
        ))).isInstanceOf(SuggestionRateLimitException.class);
    }

    @Test
    void rejectsInvalidQueriesLimitsAndCursors() {
        SuggestionRepository repository =
                mock(SuggestionRepository.class);
        SearchCursorCodec cursors = mock(SearchCursorCodec.class);
        SuggestionRateLimiter limiter =
                mock(SuggestionRateLimiter.class);
        when(limiter.allow("client")).thenReturn(true);
        when(cursors.decode("bad")).thenThrow(
                new IllegalArgumentException("bad")
        );
        SuggestionService service = service(
                repository,
                cursors,
                limiter
        );

        assertInvalid(service, null, null, 8);
        assertInvalid(service, "x", null, 8);
        assertInvalid(service, "a\u0000b", null, 8);
        assertInvalid(service, "story", null, 0);
        assertInvalid(service, "story", "bad", 8);
    }

    @Test
    void keepsTheHotPathBoundedUnderRepeatedLoad() {
        SuggestionRepository repository =
                mock(SuggestionRepository.class);
        SearchCursorCodec cursors = mock(SearchCursorCodec.class);
        SuggestionRateLimiter limiter =
                mock(SuggestionRateLimiter.class);
        when(limiter.allow("load")).thenReturn(true);
        when(repository.find("story", null, 8)).thenReturn(
                new SuggestionRepository.SuggestionPage(
                        List.of(),
                        null,
                        false
                )
        );
        SuggestionService service = service(
                repository,
                cursors,
                limiter
        );
        long started = System.nanoTime();

        for (int index = 0; index < 1_000; index++) {
            service.suggest(new SuggestionOperations.SuggestionRequest(
                    "story", null, 8, "load"
            ));
        }

        long elapsedMillis =
                (System.nanoTime() - started) / 1_000_000;
        assertThat(elapsedMillis).isLessThan(2_000);
    }

    private static SuggestionService service(
            SuggestionRepository repository,
            SearchCursorCodec cursors,
            SuggestionRateLimiter limiter
    ) {
        return new SuggestionService(repository, cursors, limiter);
    }

    private static void assertInvalid(
            SuggestionService service,
            String query,
            String cursor,
            int limit
    ) {
        assertThatThrownBy(() -> service.suggest(
                new SuggestionOperations.SuggestionRequest(
                        query,
                        cursor,
                        limit,
                        "client"
                )
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
