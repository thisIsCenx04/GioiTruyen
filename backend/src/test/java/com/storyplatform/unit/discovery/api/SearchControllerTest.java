package com.storyplatform.unit.discovery.api;

import com.storyplatform.discovery.api.SearchController;
import com.storyplatform.discovery.application.SearchOperations;
import com.storyplatform.shared.api.ApiException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SearchControllerTest {

    @Test
    void exposesShortPublicCaching() {
        SearchOperations operations = mock(SearchOperations.class);
        when(operations.search(any())).thenReturn(
                new SearchOperations.SearchResponse(
                        List.of(), null, false, Map.of(), 1
                )
        );

        var response = new SearchController(operations).search(
                "story", null, null, null, null, 20
        );

        assertThat(response.getHeaders().getCacheControl())
                .contains("public")
                .contains("max-age=30");
    }

    @Test
    void mapsRejectedQueriesToApiProblems() {
        SearchOperations operations = mock(SearchOperations.class);
        when(operations.search(any())).thenThrow(
                new IllegalArgumentException("invalid")
        );

        assertThatThrownBy(() ->
                new SearchController(operations).search(
                        "x", null, null, null, null, 20
                )).isInstanceOf(ApiException.class);
    }
}
