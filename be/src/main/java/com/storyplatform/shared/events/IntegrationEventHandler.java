package com.storyplatform.shared.events;

/**
 * Handles one version of an integration event.
 *
 * <p>Database side effects join the inbox transaction. External side effects
 * must use the event ID as their provider idempotency key.</p>
 */
public interface IntegrationEventHandler {

    String consumer();

    String eventType();

    int eventVersion();

    void handle(OutboxDelivery event);
}
