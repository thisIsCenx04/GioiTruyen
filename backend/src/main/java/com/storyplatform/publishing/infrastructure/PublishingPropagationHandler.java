package com.storyplatform.publishing.infrastructure;

import com.storyplatform.shared.events.IntegrationEventHandler;
import com.storyplatform.shared.events.OutboxDelivery;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.List;
import java.util.Objects;

public final class PublishingPropagationHandler
        implements IntegrationEventHandler {

    public static final String COLLECTION =
            "publishing_propagation_tasks";

    private final MongoTemplate mongo;
    private final String eventType;
    private final Channel channel;

    public PublishingPropagationHandler(
            MongoTemplate mongo,
            String eventType,
            Channel channel
    ) {
        this.mongo = Objects.requireNonNull(mongo, "mongo");
        this.eventType = Objects.requireNonNull(eventType, "eventType");
        this.channel = Objects.requireNonNull(channel, "channel");
    }

    @Override
    public String consumer() {
        return "publishing-" + channel.consumerName() + "-v1";
    }

    @Override
    public String eventType() {
        return eventType;
    }

    @Override
    public int eventVersion() {
        return 1;
    }

    @Override
    public void handle(OutboxDelivery event) {
        if (!eventType.equals(event.eventType())) {
            throw new IllegalArgumentException(
                    "propagation event type does not match handler"
            );
        }
        String id = event.eventId() + ":" + channel.name();
        mongo.upsert(
                Query.query(Criteria.where("_id").is(id)),
                new Update()
                        .setOnInsert("_id", id)
                        .setOnInsert("eventId", event.eventId())
                        .setOnInsert("idempotencyKey", event.eventId())
                        .setOnInsert("eventType", event.eventType())
                        .setOnInsert("eventVersion", event.eventVersion())
                        .setOnInsert("channel", channel.name())
                        .setOnInsert("state", "PENDING")
                        .setOnInsert("aggregateType", event.aggregateType())
                        .setOnInsert("aggregateId", event.aggregateId())
                        .setOnInsert("teamId", event.teamId())
                        .setOnInsert("correlationId", event.correlationId())
                        .setOnInsert("payload", event.payload())
                        .setOnInsert("targets", targets(event))
                        .setOnInsert("attempts", 0)
                        .setOnInsert("availableAt", event.occurredAt())
                        .setOnInsert("createdAt", event.occurredAt()),
                COLLECTION
        );
    }

    private List<String> targets(OutboxDelivery event) {
        return switch (channel) {
            case EDGE -> List.of(
                    "cloudflare:tag:"
                            + event.aggregateType()
                            + ":"
                            + event.aggregateId(),
                    "next:isr:"
                            + event.aggregateType()
                            + ":"
                            + event.aggregateId()
            );
            case NOTIFICATION -> List.of(
                    event.eventType().equals(
                            "publishing.chapter.published"
                    )
                            ? "team-followers:" + event.teamId()
                            : "team-owners:" + event.teamId()
            );
        };
    }

    public enum Channel {
        EDGE("edge"),
        NOTIFICATION("notification");

        private final String consumerName;

        Channel(String consumerName) {
            this.consumerName = consumerName;
        }

        String consumerName() {
            return consumerName;
        }
    }
}
