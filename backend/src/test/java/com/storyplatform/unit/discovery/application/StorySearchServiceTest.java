package com.storyplatform.unit.discovery.application;

import com.storyplatform.discovery.application.SearchOperations;
import com.storyplatform.discovery.application.StorySearchService;
import com.storyplatform.discovery.application.port.SearchCursorCodec;
import com.storyplatform.discovery.application.port.StorySearchRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StorySearchServiceTest {

    @Test
    void normalizesInputAndWrapsTheAtlasCursor() {
        StorySearchRepository repository =
                mock(StorySearchRepository.class);
        SearchCursorCodec cursors = mock(SearchCursorCodec.class);
        when(cursors.decode("signed")).thenReturn("atlas");
        when(cursors.encode("next-atlas")).thenReturn("next-signed");
        when(repository.search(any())).thenReturn(
                new StorySearchRepository.SearchPage(
                        List.of(),
                        "next-atlas",
                        true,
                        Map.of()
                )
        );
        var service = new StorySearchService(repository, cursors);

        var response = service.search(new SearchOperations.SearchRequest(
                "  Kiếm   hiệp  ",
                "10000000-0000-4000-8000-000000000001",
                "completed",
                "original",
                "signed",
                20
        ));

        ArgumentCaptor<StorySearchRepository.SearchQuery> request =
                ArgumentCaptor.forClass(
                        StorySearchRepository.SearchQuery.class
                );
        verify(repository).search(request.capture());
        assertThat(request.getValue().text()).isEqualTo("Kiếm hiệp");
        assertThat(request.getValue().completionStatus())
                .isEqualTo("COMPLETED");
        assertThat(request.getValue().atlasCursor()).isEqualTo("atlas");
        assertThat(response.nextCursor()).isEqualTo("next-signed");
        assertThat(response.hasMore()).isTrue();
    }

    @Test
    void rejectsAbusiveQueriesFiltersLimitsAndCursors() {
        StorySearchRepository repository =
                mock(StorySearchRepository.class);
        SearchCursorCodec cursors = mock(SearchCursorCodec.class);
        when(cursors.decode("bad")).thenThrow(
                new IllegalArgumentException("bad")
        );
        var service = new StorySearchService(repository, cursors);

        assertInvalid(service, null, null, null, null, 20);
        assertInvalid(service, "x", null, null, null, 20);
        assertInvalid(service, "a\u0000b", null, null, null, 20);
        assertInvalid(service, "story", "bad", null, null, 20);
        assertInvalid(service, "story", null, "draft", null, 20);
        assertInvalid(service, "story", null, null, "other", 20);
        assertInvalid(service, "story", null, null, null, 0);
        assertThatThrownBy(() -> service.search(
                new SearchOperations.SearchRequest(
                        "story", null, null, null, "bad", 20
                )
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private static void assertInvalid(
            StorySearchService service,
            String query,
            String category,
            String status,
            String origin,
            int limit
    ) {
        assertThatThrownBy(() -> service.search(
                new SearchOperations.SearchRequest(
                        query,
                        category,
                        status,
                        origin,
                        null,
                        limit
                )
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
