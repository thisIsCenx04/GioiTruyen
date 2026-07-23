package com.storyplatform.unit.shared.events.persistence;

import com.storyplatform.shared.events.IntegrationEventHandler;
import com.storyplatform.shared.events.OutboxDelivery;
import com.storyplatform.shared.events.persistence.InboxDispatcher;
import com.storyplatform.shared.events.persistence.InboxReceipt;
import com.storyplatform.shared.events.persistence.OutboxMessage;
import com.storyplatform.shared.events.persistence.OutboxStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InboxDispatcherTest {

    private static final Instant NOW = Instant.parse(
            "2026-01-01T00:00:00Z"
    );

    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    private final IntegrationEventHandler handler = handler();
    private final InboxDispatcher dispatcher = new InboxDispatcher(
            mongoTemplate,
            List.of(handler),
            Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    void firstDeliveryRecordsReceiptThenInvokesHandler() {
        OutboxMessage message = message();
        when(mongoTemplate.exists(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(InboxReceipt.class)
        )).thenReturn(false);

        InboxDispatcher.DispatchResult result = dispatcher.dispatch(message);

        assertThat(result).isEqualTo(
                InboxDispatcher.DispatchResult.PROCESSED
        );
        ArgumentCaptor<InboxReceipt> receipt = ArgumentCaptor.forClass(
                InboxReceipt.class
        );
        verify(mongoTemplate).insert(receipt.capture());
        assertThat(receipt.getValue().id()).isEqualTo(
                "search-indexer:event-1"
        );
        verify(handler).handle(OutboxDelivery.from(message));
    }

    @Test
    void replayWithExistingReceiptDoesNotInvokeHandlerAgain() {
        when(mongoTemplate.exists(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(InboxReceipt.class)
        )).thenReturn(true);

        InboxDispatcher.DispatchResult result = dispatcher.dispatch(message());

        assertThat(result).isEqualTo(
                InboxDispatcher.DispatchResult.DUPLICATE
        );
        verify(mongoTemplate, never()).insert(
                org.mockito.ArgumentMatchers.any(InboxReceipt.class)
        );
        verify(handler, never()).handle(
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void distinctConsumersReceiveTheSameEventVersion() {
        IntegrationEventHandler notification = handler(
                "notification-sender"
        );
        InboxDispatcher fanout = new InboxDispatcher(
                mongoTemplate,
                List.of(handler, notification),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        when(mongoTemplate.exists(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(InboxReceipt.class)
        )).thenReturn(false);
        OutboxMessage message = message();

        assertThat(fanout.dispatch(message)).isEqualTo(
                InboxDispatcher.DispatchResult.PROCESSED
        );

        verify(handler).handle(OutboxDelivery.from(message));
        verify(notification).handle(OutboxDelivery.from(message));
    }

    @Test
    void duplicateHandlerContractIsRejectedAtStartup() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new InboxDispatcher(
                        mongoTemplate,
                        List.of(handler, handler()),
                        Clock.systemUTC()
                )
        ).withMessage(
                "Duplicate handler registration "
                        + "publishing.story.published/1/search-indexer"
        );
    }

    private static IntegrationEventHandler handler() {
        return handler("search-indexer");
    }

    private static IntegrationEventHandler handler(String consumer) {
        IntegrationEventHandler handler = mock(
                IntegrationEventHandler.class
        );
        when(handler.consumer()).thenReturn(consumer);
        when(handler.eventType()).thenReturn(
                "publishing.story.published"
        );
        when(handler.eventVersion()).thenReturn(1);
        return handler;
    }

    private static OutboxMessage message() {
        return new OutboxMessage(
                "event-1",
                "publishing.story.published",
                1,
                NOW.minusSeconds(1),
                "request-1",
                "story",
                "story-1",
                "user-1",
                "team-1",
                "application/json",
                "{\"revision\":3}",
                OutboxStatus.PROCESSING,
                1,
                NOW,
                "worker-1",
                NOW.plusSeconds(30),
                null,
                NOW.minusSeconds(1),
                null
        );
    }
}
