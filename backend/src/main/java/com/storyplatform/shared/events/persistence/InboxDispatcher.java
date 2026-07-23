package com.storyplatform.shared.events.persistence;

import com.storyplatform.shared.events.IntegrationEventHandler;
import com.storyplatform.shared.events.OutboxDelivery;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class InboxDispatcher {

    private final MongoTemplate mongoTemplate;
    private final Map<HandlerKey, List<IntegrationEventHandler>> handlers;
    private final Clock clock;

    public InboxDispatcher(
            MongoTemplate mongoTemplate,
            List<IntegrationEventHandler> handlers,
            Clock clock
    ) {
        this.mongoTemplate = mongoTemplate;
        this.handlers = indexHandlers(handlers);
        this.clock = clock;
    }

    @Transactional
    public DispatchResult dispatch(OutboxMessage message) {
        List<IntegrationEventHandler> matchingHandlers = handlers.get(
                new HandlerKey(
                message.eventType(),
                message.eventVersion()
        ));
        if (matchingHandlers == null) {
            throw new IllegalStateException(
                    "No handler for event type/version "
                            + message.eventType()
                            + "/"
                            + message.eventVersion()
            );
        }

        boolean processed = false;
        for (IntegrationEventHandler handler : matchingHandlers) {
            InboxReceipt receipt = InboxReceipt.from(
                    handler.consumer(),
                    message,
                    clock.instant()
            );
            if (mongoTemplate.exists(
                    Query.query(Criteria.where("_id").is(receipt.id())),
                    InboxReceipt.class
            )) {
                continue;
            }

            mongoTemplate.insert(receipt);
            handler.handle(OutboxDelivery.from(message));
            processed = true;
        }
        return processed ? DispatchResult.PROCESSED : DispatchResult.DUPLICATE;
    }

    private static Map<HandlerKey, List<IntegrationEventHandler>> indexHandlers(
            List<IntegrationEventHandler> handlers
    ) {
        Map<HandlerKey, List<IntegrationEventHandler>> indexed =
                new HashMap<>();
        Set<HandlerRegistration> registrations = new HashSet<>();
        for (IntegrationEventHandler handler : handlers) {
            HandlerKey key = new HandlerKey(
                    handler.eventType(),
                    handler.eventVersion()
            );
            HandlerRegistration registration = new HandlerRegistration(
                    key,
                    handler.consumer()
            );
            if (!registrations.add(registration)) {
                throw new IllegalArgumentException(
                        "Duplicate handler registration "
                                + key.eventType()
                                + "/"
                                + key.eventVersion()
                                + "/"
                                + handler.consumer()
                );
            }
            indexed.computeIfAbsent(key, ignored -> new ArrayList<>())
                    .add(handler);
        }
        indexed.replaceAll((key, value) -> List.copyOf(value));
        return Map.copyOf(indexed);
    }

    public enum DispatchResult {
        PROCESSED,
        DUPLICATE
    }

    private record HandlerKey(String eventType, int eventVersion) {
    }

    private record HandlerRegistration(
            HandlerKey key,
            String consumer
    ) {
    }
}
