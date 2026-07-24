package com.storyplatform.unit.discovery.api;

import com.storyplatform.discovery.api.HomeController;
import com.storyplatform.discovery.application.HomeOperations;
import com.storyplatform.discovery.application.HomeReadModel;
import com.storyplatform.shared.api.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HomeControllerTest {

    @Test
    void exposesVersionEtagCacheAndConditionalResponse() {
        HomeOperations homes = mock(HomeOperations.class);
        when(homes.get("vi-VN")).thenReturn(model());
        HomeController controller = new HomeController(homes);

        var fresh = controller.get("vi-VN", null);
        var unchanged = controller.get(
                "vi-VN",
                fresh.getHeaders().getETag()
        );

        assertThat(fresh.getHeaders().getETag()).isEqualTo("\"home-v1\"");
        assertThat(fresh.getHeaders().getCacheControl())
                .contains("public")
                .contains("max-age=120");
        assertThat(unchanged.getStatusCode())
                .isEqualTo(HttpStatus.NOT_MODIFIED);
    }

    @Test
    void mapsInvalidLocalesToApiProblems() {
        HomeOperations homes = mock(HomeOperations.class);
        when(homes.get("bad")).thenThrow(
                new IllegalArgumentException("locale is invalid")
        );

        assertThatThrownBy(() ->
                new HomeController(homes).get("bad", null))
                .isInstanceOf(ApiException.class);
    }

    private static HomeReadModel model() {
        return new HomeReadModel(
                "vi-VN",
                "v1",
                Instant.EPOCH,
                List.of()
        );
    }
}
