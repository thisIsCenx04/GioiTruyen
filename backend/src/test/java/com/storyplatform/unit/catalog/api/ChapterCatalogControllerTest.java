package com.storyplatform.unit.catalog.api;

import com.storyplatform.catalog.api.ChapterCatalogController;
import com.storyplatform.catalog.application.ChapterCatalogOperations;
import com.storyplatform.catalog.application.CatalogRequestException;
import com.storyplatform.shared.api.ApiException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChapterCatalogControllerTest {

    @Test
    void exposesAReusablePublicCacheContract() {
        ChapterCatalogOperations operations =
                mock(ChapterCatalogOperations.class);
        when(operations.list("story", null, 20)).thenReturn(
                new ChapterCatalogOperations.ChapterPage(
                        List.of(),
                        null,
                        false
                )
        );

        var response = new ChapterCatalogController(operations)
                .list("story", null, 20);

        assertThat(response.getHeaders().getCacheControl())
                .contains("public")
                .contains("max-age=120")
                .contains("stale-while-revalidate=600");
    }

    @Test
    void mapsMissingStoriesAndInvalidRequestsToApiProblems() {
        ChapterCatalogOperations operations =
                mock(ChapterCatalogOperations.class);
        var controller = new ChapterCatalogController(operations);
        when(operations.list("missing", null, 20))
                .thenThrow(new CatalogRequestException(
                        "STORY_NOT_FOUND",
                        "missing"
                ));
        when(operations.list("story", null, 0))
                .thenThrow(new CatalogRequestException(
                        "LIMIT_INVALID",
                        "invalid"
                ));

        assertThatThrownBy(() -> controller.list("missing", null, 20))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> controller.list("story", null, 0))
                .isInstanceOf(ApiException.class);
    }
}
