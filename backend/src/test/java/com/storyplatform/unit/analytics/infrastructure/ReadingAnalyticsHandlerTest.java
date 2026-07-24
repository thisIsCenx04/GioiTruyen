package com.storyplatform.unit.analytics.infrastructure;

import com.storyplatform.analytics.application.RawReadingEvent;
import com.storyplatform.analytics.application.port
        .RawReadingEventRepository;
import com.storyplatform.analytics.application.port
        .ReadingSessionPseudonymizer;
import com.storyplatform.analytics.infrastructure
        .ReadingCompletionAnalyticsHandler;
import com.storyplatform.analytics.infrastructure
        .ReadingHeartbeatAnalyticsHandler;
import com.storyplatform.reading.application.contract.ReadingSessionEvents;
import com.storyplatform.shared.events.OutboxDelivery;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReadingAnalyticsHandlerTest {

    private static final String SESSION =
            "10000000-0000-4000-8000-000000000001";
    private static final String STORY =
            "20000000-0000-4000-8000-000000000001";
    private static final String CHAPTER =
            "30000000-0000-4000-8000-000000000001";
    private static final String EVENT =
            "40000000-0000-4000-8000-000000000001";
    private static final Instant NOW =
            Instant.parse("2026-07-24T00:00:00Z");
    private final ObjectMapper mapper = new ObjectMapper();
    private final RawReadingEventRepository repository =
            mock(RawReadingEventRepository.class);
    private final ReadingSessionPseudonymizer pseudonyms =
            mock(ReadingSessionPseudonymizer.class);

    @Test
    void storesHeartbeatBatchWithoutIdentityOrRequestMetadata()
            throws Exception {
        when(pseudonyms.pseudonymize(SESSION))
                .thenReturn("a".repeat(64));
        var handler = new ReadingHeartbeatAnalyticsHandler(
                repository,
                pseudonyms,
                mapper
        );
        var payload = new ReadingSessionEvents.HeartbeatBatchAccepted(
                SESSION,
                "actor-reference",
                STORY,
                CHAPTER,
                EVENT,
                List.of(
                        new ReadingSessionEvents.Heartbeat(
                                1, NOW, 25, 10
                        ),
                        new ReadingSessionEvents.Heartbeat(
                                2, NOW.plusSeconds(10), 50, 10
                        )
                )
        );

        handler.handle(delivery(
                ReadingSessionEvents.HEARTBEAT_ACCEPTED,
                mapper.writeValueAsString(payload),
                SESSION,
                EVENT
        ));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RawReadingEvent>> captured =
                ArgumentCaptor.forClass(List.class);
        verify(repository).append(captured.capture());
        assertThat(captured.getValue()).hasSize(2);
        assertThat(captured.getValue().getFirst().sessionRef())
                .isEqualTo("a".repeat(64));
        assertThat(captured.getValue().getFirst().activeSeconds())
                .isEqualTo(10);
        assertThat(handler.consumer()).isEqualTo("raw-reading-heartbeats");
        assertThat(handler.eventVersion()).isEqualTo(1);
    }

    @Test
    void storesCompletionAndRejectsMismatchedOrMalformedEvents()
            throws Exception {
        when(pseudonyms.pseudonymize(SESSION))
                .thenReturn("b".repeat(64));
        var handler = new ReadingCompletionAnalyticsHandler(
                repository,
                pseudonyms,
                mapper
        );
        var payload = new ReadingSessionEvents.ReadingSessionCompleted(
                SESSION,
                "actor-reference",
                STORY,
                CHAPTER,
                EVENT,
                2,
                NOW,
                100
        );
        String json = mapper.writeValueAsString(payload);

        handler.handle(delivery(
                ReadingSessionEvents.COMPLETED,
                json,
                SESSION,
                EVENT
        ));

        verify(repository).append(anyList());
        assertThat(handler.consumer()).isEqualTo("raw-reading-completions");
        assertThat(handler.eventVersion()).isEqualTo(1);
        assertThatThrownBy(() -> handler.handle(delivery(
                ReadingSessionEvents.COMPLETED,
                json,
                "50000000-0000-4000-8000-000000000001",
                EVENT
        ))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> handler.handle(delivery(
                ReadingSessionEvents.COMPLETED,
                "{invalid",
                SESSION,
                EVENT
        ))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMismatchedHeartbeatEnvelope() throws Exception {
        var handler = new ReadingHeartbeatAnalyticsHandler(
                repository,
                pseudonyms,
                mapper
        );
        var payload = new ReadingSessionEvents.HeartbeatBatchAccepted(
                SESSION,
                "actor-reference",
                STORY,
                CHAPTER,
                EVENT,
                List.of(new ReadingSessionEvents.Heartbeat(
                        1, NOW, 25, 10
                ))
        );
        String json = mapper.writeValueAsString(payload);

        assertThatThrownBy(() -> handler.handle(delivery(
                ReadingSessionEvents.HEARTBEAT_ACCEPTED,
                json,
                SESSION,
                "50000000-0000-4000-8000-000000000001"
        ))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> handler.handle(delivery(
                ReadingSessionEvents.HEARTBEAT_ACCEPTED,
                "not-json",
                SESSION,
                EVENT
        ))).isInstanceOf(IllegalArgumentException.class);
    }

    private static OutboxDelivery delivery(
            String type,
            String payload,
            String aggregateId,
            String correlationId
    ) {
        return new OutboxDelivery(
                EVENT,
                type,
                1,
                NOW.plusSeconds(30),
                correlationId,
                "reading_session",
                aggregateId,
                null,
                null,
                null,
                payload
        );
    }
}
