package com.storyplatform.unit.reading.api;

import com.storyplatform.reading.api.ReadingProgressController;
import com.storyplatform.reading.api.SynchronizeReadingProgressRequest;
import com.storyplatform.reading.application.ReadingProgressOperations;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReadingProgressControllerTest {

    @Test
    void returnsNoStoreEtagAndPassesQuotedVersion() {
        var operations = mock(ReadingProgressOperations.class);
        Instant now = Instant.parse("2026-07-24T00:00:00Z");
        var view = new ReadingProgressOperations.ProgressView(
                "20000000-0000-4000-8000-000000000001",
                "30000000-0000-4000-8000-000000000001",
                50,
                now,
                now,
                3
        );
        when(operations.synchronize(
                any(), any(), any(Long.class), any()
        )).thenReturn(view);
        var controller = new ReadingProgressController(operations);
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject("10000000-0000-4000-8000-000000000001")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .build();
        var request = new SynchronizeReadingProgressRequest(
                view.chapterId(),
                50,
                now
        );

        var response = controller.synchronize(
                jwt,
                view.storyId(),
                "\"2\"",
                request
        );

        assertThat(response.getHeaders().getETag()).isEqualTo("\"3\"");
        assertThat(response.getHeaders().getCacheControl())
                .contains("no-store");
        verify(operations).synchronize(
                jwt.getSubject(),
                view.storyId(),
                2L,
                new ReadingProgressOperations.SyncCommand(
                        view.chapterId(), 50, now
                )
        );
    }
}
