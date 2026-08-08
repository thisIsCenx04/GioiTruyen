package com.storyplatform.unit.discovery.api;

import com.storyplatform.discovery.api.SuggestionController;
import com.storyplatform.discovery.application.SuggestionOperations;
import com.storyplatform.discovery.application.SuggestionRateLimitException;
import com.storyplatform.discovery.application
        .SuggestionRateLimitUnavailableException;
import com.storyplatform.shared.api.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SuggestionControllerTest {

    @Test
    void forwardsClientSubjectAndExposesShortCaching() {
        SuggestionOperations operations =
                mock(SuggestionOperations.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("203.0.113.10");
        when(operations.suggest(any())).thenReturn(
                new SuggestionOperations.SuggestionResponse(
                        List.of(),
                        null,
                        false
                )
        );

        var response = new SuggestionController(operations).suggest(
                "story", null, 8, request
        );

        assertThat(response.getHeaders().getCacheControl())
                .contains("public")
                .contains("max-age=30");
    }

    @Test
    void mapsRateInvalidAndUnavailableFailures() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        SuggestionOperations operations =
                mock(SuggestionOperations.class);
        SuggestionController controller =
                new SuggestionController(operations);
        when(operations.suggest(any()))
                .thenThrow(new SuggestionRateLimitException(60))
                .thenThrow(new SuggestionRateLimitUnavailableException(
                        new IllegalStateException("down")
                ))
                .thenThrow(new IllegalArgumentException("bad"));

        assertThatThrownBy(() ->
                controller.suggest("story", null, 8, request))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() ->
                controller.suggest("story", null, 8, request))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() ->
                controller.suggest("x", null, 8, request))
                .isInstanceOf(ApiException.class);
    }
}
