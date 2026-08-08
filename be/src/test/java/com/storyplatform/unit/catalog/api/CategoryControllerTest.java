package com.storyplatform.unit.catalog.api;

import com.storyplatform.catalog.api.CategoryController;
import com.storyplatform.catalog.application.CategoryOperations;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CategoryControllerTest {

    @Test
    void returnsTheGroupedTaxonomyWithPublicCacheHeaders() {
        CategoryOperations operations = mock(CategoryOperations.class);
        var taxonomy = new CategoryOperations.TaxonomyView(
                "version-1",
                List.of()
        );
        when(operations.getTaxonomy()).thenReturn(taxonomy);

        var response = new CategoryController(operations).getTaxonomy();

        assertThat(response.getBody()).isSameAs(taxonomy);
        assertThat(response.getHeaders().getCacheControl())
                .contains("public")
                .contains("max-age=300")
                .contains("stale-while-revalidate=3600");
    }
}
