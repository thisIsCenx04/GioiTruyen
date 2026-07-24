package com.storyplatform.unit.reading.api;

import com.storyplatform.reading.api.ReadingCompletionController;
import com.storyplatform.reading.api.ReadingCompletionRequest;
import com.storyplatform.reading.application.ReadingCompletionOperations;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReadingCompletionControllerTest {

    @Test
    void queuesCompletionWithNoStore() {
        var operations = mock(ReadingCompletionOperations.class);
        when(operations.complete(eq("session"), eq("token"), any()))
                .thenReturn(
                        new ReadingCompletionOperations.CompletionReceipt(
                                "completion",
                                "COMPLETION_PENDING",
                                false
                        )
                );

        var response = new ReadingCompletionController(operations)
                .complete(
                        "session",
                        "token",
                        new ReadingCompletionRequest(
                                "completion",
                                1,
                                Instant.parse(
                                        "2026-07-24T00:00:00Z"
                                ),
                                100
                        )
                );

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getHeaders().getCacheControl())
                .contains("no-store");
    }
}
