package com.storyplatform.unit.discovery.api;

import com.storyplatform.discovery.api.RankingController;
import com.storyplatform.discovery.application.RankingOperations;
import com.storyplatform.shared.api.ApiException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RankingControllerTest {

    @Test
    void servesCacheableStoryAndTeamRankingsAndMapsInvalidQuery() {
        RankingOperations operations = mock(RankingOperations.class);
        var page = new RankingOperations.RankingPage(
                "STORY",
                "WEEK",
                "VALID_VIEWS",
                Instant.EPOCH,
                false,
                List.of()
        );
        when(operations.stories(
                "WEEK", "VALID_VIEWS", Instant.EPOCH, 20
        )).thenReturn(page);
        when(operations.teams(
                "WEEK", "VALID_VIEWS", Instant.EPOCH, 20
        )).thenReturn(new RankingOperations.RankingPage(
                "TEAM",
                "WEEK",
                "VALID_VIEWS",
                Instant.EPOCH,
                false,
                List.of()
        ));
        var controller = new RankingController(operations);

        assertThat(controller.stories(
                "WEEK", "VALID_VIEWS", Instant.EPOCH, 20
        ).getHeaders().getCacheControl()).contains("public");
        assertThat(controller.teams(
                "WEEK", "VALID_VIEWS", Instant.EPOCH, 20
        ).getBody().subject()).isEqualTo("TEAM");

        when(operations.stories("BAD", "BAD", null, 20))
                .thenThrow(new IllegalArgumentException("invalid"));
        assertThatThrownBy(() ->
                controller.stories("BAD", "BAD", null, 20))
                .isInstanceOf(ApiException.class);
    }
}
