package com.storyplatform.unit.reading.api;

import com.storyplatform.reading.api.ReadingHistoryController;
import com.storyplatform.reading.application.ReadingHistoryOperations;
import com.storyplatform.reading.application.ReadingProgressException;
import com.storyplatform.shared.api.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReadingHistoryControllerTest {

    @Test
    void listsPrivateHistoryWithNoStore() {
        var operations = mock(ReadingHistoryOperations.class);
        var page = new ReadingHistoryOperations.HistoryPage(
                List.of(), null, false
        );
        Jwt jwt = jwt();
        when(operations.list(jwt.getSubject(), "next", 25))
                .thenReturn(page);

        var response = new ReadingHistoryController(operations)
                .list(jwt, "next", 25);

        assertThat(response.getBody()).isEqualTo(page);
        assertThat(response.getHeaders().getCacheControl())
                .contains("no-store");
    }

    @Test
    void deletesOnlyTheAuthenticatedUsersStory() {
        var operations = mock(ReadingHistoryOperations.class);
        Jwt jwt = jwt();
        String storyId = "20000000-0000-4000-8000-000000000001";

        var response = new ReadingHistoryController(operations)
                .delete(jwt, storyId);

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        assertThat(response.getHeaders().getCacheControl())
                .contains("no-store");
        verify(operations).delete(jwt.getSubject(), storyId);
    }

    @Test
    void mapsApplicationFailuresToApiStatuses() {
        var operations = mock(ReadingHistoryOperations.class);
        Jwt jwt = jwt();
        when(operations.list(jwt.getSubject(), null, 20))
                .thenThrow(failure(ReadingProgressException.Kind.INVALID));

        assertThatThrownBy(() -> new ReadingHistoryController(operations)
                .list(jwt, null, 20))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).status())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        org.mockito.Mockito.doThrow(failure(
                ReadingProgressException.Kind.NOT_FOUND
        )).when(operations).delete(jwt.getSubject(), "missing");
        assertThatThrownBy(() -> new ReadingHistoryController(operations)
                .delete(jwt, "missing"))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).status())
                .isEqualTo(HttpStatus.NOT_FOUND);

        when(operations.list(jwt.getSubject(), "race", 20))
                .thenThrow(failure(ReadingProgressException.Kind.CONFLICT));
        assertThatThrownBy(() -> new ReadingHistoryController(operations)
                .list(jwt, "race", 20))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).status())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    private static Jwt jwt() {
        Instant now = Instant.parse("2026-07-24T00:00:00Z");
        return Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject("10000000-0000-4000-8000-000000000001")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .build();
    }

    private static ReadingProgressException failure(
            ReadingProgressException.Kind kind
    ) {
        return new ReadingProgressException(
                "READING_HISTORY_TEST",
                "test failure",
                kind
        );
    }
}
