package com.storyplatform.unit.reading.api;

import com.storyplatform.reading.api.ReadingHeartbeatController;
import com.storyplatform.reading.api.ReadingHeartbeatRequest;
import com.storyplatform.reading.application.ReadingHeartbeatOperations;
import com.storyplatform.reading.application.ReadingSessionException;
import com.storyplatform.shared.api.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReadingHeartbeatControllerTest {

    @Test
    void acceptsAHeartbeatBatchWithNoStore() {
        var operations = mock(ReadingHeartbeatOperations.class);
        when(operations.ingest(eq("session"), eq("token"), any()))
                .thenReturn(
                        new ReadingHeartbeatOperations.HeartbeatReceipt(
                                "batch",
                                2,
                                false
                        )
                );
        var response = new ReadingHeartbeatController(operations).ingest(
                "session",
                "token",
                request()
        );

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getHeaders().getCacheControl())
                .contains("no-store");
        assertThat(response.getBody().nextSequence()).isEqualTo(2);
    }

    @Test
    void mapsInactiveAndInvalidSessionFailures() {
        var operations = mock(ReadingHeartbeatOperations.class);
        when(operations.ingest(any(), any(), any()))
                .thenThrow(new ReadingSessionException(
                        "READING_SESSION_NOT_ACTIVE",
                        "inactive",
                        ReadingSessionException.Kind.NOT_FOUND
                ))
                .thenThrow(new ReadingSessionException(
                        "READING_HEARTBEAT_INVALID",
                        "invalid",
                        ReadingSessionException.Kind.INVALID
                ));
        var controller = new ReadingHeartbeatController(operations);

        assertThatThrownBy(() -> controller.ingest(
                "session", "token", request()
        )).isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).status())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThatThrownBy(() -> controller.ingest(
                "session", "token", request()
        )).isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).status())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private static ReadingHeartbeatRequest request() {
        return new ReadingHeartbeatRequest(
                "batch",
                List.of(new ReadingHeartbeatRequest.Item(
                        1,
                        Instant.parse("2026-07-24T00:00:00Z"),
                        50,
                        15
                ))
        );
    }
}
