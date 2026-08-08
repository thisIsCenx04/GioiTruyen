package com.storyplatform.unit.catalog.api;

import com.storyplatform.catalog.api.StoryCatalogController;
import com.storyplatform.catalog.application.PublicStoryProjection;
import com.storyplatform.catalog.application.StoryCatalogOperations;
import com.storyplatform.catalog.domain.Story;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StoryCatalogControllerTest {

    @Test
    void exposesPublicCacheAndConditionalEtagContracts() {
        StoryCatalogOperations operations =
                mock(StoryCatalogOperations.class);
        PublicStoryProjection story = story();
        when(operations.get("story")).thenReturn(story);
        StoryCatalogController controller =
                new StoryCatalogController(operations);

        var fresh = controller.get("story", null);
        String etag = fresh.getHeaders().getETag();
        var unchanged = controller.get("story", etag);

        assertThat(fresh.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fresh.getHeaders().getCacheControl())
                .contains("public")
                .contains("max-age=120");
        assertThat(unchanged.getStatusCode())
                .isEqualTo(HttpStatus.NOT_MODIFIED);
        assertThat(unchanged.getBody()).isNull();
    }

    @Test
    void mapsTheListQueryToTheApplicationContract() {
        StoryCatalogOperations operations =
                mock(StoryCatalogOperations.class);
        when(operations.list(any())).thenReturn(
                new StoryCatalogOperations.StoryPage(
                        List.of(),
                        null,
                        false
                )
        );
        var response = new StoryCatalogController(operations).list(
                "fantasy,romance",
                "ongoing",
                "original",
                null,
                "published_desc",
                null,
                20
        );

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getHeaders().getCacheControl())
                .contains("stale-while-revalidate=600");
    }

    private static PublicStoryProjection story() {
        Instant now = Instant.parse("2026-07-24T00:00:00Z");
        return new PublicStoryProjection(
                "20000000-0000-4000-8000-000000000001",
                "20000000-0000-4000-8000-000000000002",
                "story",
                "Story",
                "Synopsis",
                List.of(),
                Story.Origin.ORIGINAL,
                "vi",
                Story.CompletionStatus.ONGOING,
                now,
                now,
                7
        );
    }
}
